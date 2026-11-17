package gemini.mods;

import gemini.Gemini;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.SharedConstants;

/**
 * The game-side half of the mod browser: one Modrinth client and installer for the session, what the running game
 * loaded, the hash index of the mods folder, and the install jobs the menu shows progress for. Jobs keep running when
 * the menu is closed; their results are read by the menu the next time it draws.
 */
public final class ModBrowser {
   /** Mod ids whose jars must never be disabled or removed from here. */
   public static final Set<String> ESSENTIAL = Set.of("gemini", "fabric-api", "fabricloader");
   private static final String HOMEPAGE = "github.com/mooreconnell24-gif/gemini-client";
   private static ModBrowser instance;
   private final ModrinthClient client;
   private final ModInstaller installer;
   private final String gameVersion;
   private final Map<String, Job> jobs = new ConcurrentHashMap<>();
   private volatile CompletableFuture<FolderIndex> folderIndex;

   /** Hashes of the jars in the mods folder and which Modrinth version each is. */
   public record FolderIndex(Map<Path, String> hashes, Map<String, Modrinth.Version> versions) {
      public Set<String> projectIds() {
         Set<String> ids = new HashSet<>();
         for (Modrinth.Version version : this.versions.values()) {
            ids.add(version.projectId());
         }

         return ids;
      }
   }

   /** An install in progress or finished. Fields are written by the worker and read by the menu. */
   public static final class Job {
      public final ProjectType type;
      public final String title;
      public volatile boolean done;
      public volatile boolean failed;
      public volatile float progress;
      public volatile String message = "Starting...";
      /** The files placed, in plan order (the project itself first). */
      public final List<Path> placed = new ArrayList<>();

      Job(ProjectType type, String title) {
         this.type = type;
         this.title = title;
      }

      /** The installed file of the project itself, or null. */
      public synchronized Path mainFile() {
         return this.placed.isEmpty() ? null : this.placed.get(0);
      }
   }

   private ModBrowser() {
      this.gameVersion = detectGameVersion();
      this.client = new ModrinthClient("gemini-client/" + Gemini.VERSION + " (" + HOMEPAGE + ")");
      Path gameDir = FabricLoader.getInstance().getGameDir();
      this.installer = new ModInstaller(gameDir, this.client::downloadNow, ModBrowser::isProtected);
   }

   public static synchronized ModBrowser get() {
      if (instance == null) {
         instance = new ModBrowser();
      }

      return instance;
   }

   public ModrinthClient client() {
      return this.client;
   }

   public ModInstaller installer() {
      return this.installer;
   }

   /** The running Minecraft version as Modrinth names it, e.g. "26.3". */
   public String gameVersion() {
      return this.gameVersion;
   }

   /** Shader packs are offered only when Iris is running. */
   public static boolean irisLoaded() {
      return FabricLoader.getInstance().isModLoaded("iris");
   }

   private static String detectGameVersion() {
      try {
         String name = SharedConstants.getCurrentVersion().name();
         if (name != null && !name.isBlank()) {
            return name;
         }
      } catch (RuntimeException ignored) {
         // fall back to the loader's view of the game below
      }

      return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("26.3");
   }

   // ---- what is installed ------------------------------------------------------------------------------------

   /** Every mod the running game loaded, with the jar files it came from. */
   public static List<InstalledMods.Loaded> loadedMods() {
      List<InstalledMods.Loaded> out = new ArrayList<>();
      for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
         List<Path> paths = List.of();
         try {
            if (container.getOrigin().getKind() == ModOrigin.Kind.PATH) {
               paths = container.getOrigin().getPaths();
            }
         } catch (RuntimeException ignored) {
            // nested or synthetic mods have no file of their own
         }

         out.add(new InstalledMods.Loaded(
            container.getMetadata().getId(), container.getMetadata().getName(), container.getMetadata().getVersion().getFriendlyString(), paths
         ));
      }

      return out;
   }

   /** Gemini's own jar, or null when it runs from a folder (a development build). */
   public static Path ownJar() {
      return FabricLoader.getInstance().getModContainer("gemini").map(c -> {
         try {
            for (Path path : c.getOrigin().getPaths()) {
               if (Files.isRegularFile(path)) {
                  return path.toAbsolutePath().normalize();
               }
            }
         } catch (RuntimeException ignored) {
            // no single origin file
         }

         return null;
      }).orElse(null);
   }

   private static boolean isProtected(Path file) {
      Path own = ownJar();
      Path normal = file.toAbsolutePath().normalize();
      if (own != null && own.equals(normal)) {
         return true;
      }

      return Files.isRegularFile(normal) && ESSENTIAL.contains(InstalledMods.readModInfo(normal).id());
   }

   /** The mods folder as a list, for the Installed tab. Blocking: call from a worker. */
   public List<InstalledMods.Entry> scanMods() throws IOException {
      return InstalledMods.scan(this.installer.modsFolder(), loadedMods(), ESSENTIAL, ownJar());
   }

   /**
    * Hashes every jar in the mods folder and asks Modrinth which versions they are. Cached until something is
    * installed, disabled or removed; a failed lookup still returns the hashes (with no versions).
    */
   public CompletableFuture<FolderIndex> folderIndex() {
      CompletableFuture<FolderIndex> current = this.folderIndex;
      if (current == null || current.isCompletedExceptionally()) {
         current = this.client.async(() -> {
            Map<Path, String> hashes = new HashMap<>();
            Path mods = this.installer.modsFolder();
            if (Files.isDirectory(mods)) {
               try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods)) {
                  for (Path path : stream) {
                     String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                     if (!name.startsWith(".") && Files.isRegularFile(path) && (name.endsWith(".jar") || name.endsWith(".jar" + InstalledMods.DISABLED_SUFFIX))) {
                        hashes.put(path, SafeFiles.sha512(path));
                     }
                  }
               }
            }

            Map<String, Modrinth.Version> versions;
            try {
               versions = this.client.versionFilesNow(new HashSet<>(hashes.values()));
            } catch (IOException e) {
               versions = Map.of();
            }

            return new FolderIndex(Map.copyOf(hashes), versions);
         });
         this.folderIndex = current;
      }

      return current;
   }

   /** Forgets the folder index after the folder changed. */
   public void folderChanged() {
      this.folderIndex = null;
   }

   /** What counts as installed for dependency resolution: loaded mods, jars in the folder, and their projects. */
   public DependencyPlan.Installed installed(FolderIndex index) {
      Set<String> modIds = new HashSet<>();
      for (InstalledMods.Loaded mod : loadedMods()) {
         modIds.add(mod.modId().toLowerCase(Locale.ROOT));
      }

      Set<String> projectIds = new HashSet<>();
      if (index != null) {
         projectIds.addAll(index.projectIds());
         for (Path path : index.hashes().keySet()) {
            if (!path.getFileName().toString().endsWith(InstalledMods.DISABLED_SUFFIX)) {
               String id = InstalledMods.readModInfo(path).id();
               if (!id.isEmpty()) {
                  modIds.add(id.toLowerCase(Locale.ROOT));
               }
            }
         }
      }

      return new DependencyPlan.Installed(projectIds, modIds);
   }

   /** Names of loaded mods that need {@code modId} to start (so disabling it would stop them loading). */
   public static List<String> dependents(String modId) {
      List<String> names = new ArrayList<>();
      if (modId == null || modId.isEmpty()) {
         return names;
      }

      for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
         if (container.getMetadata().getId().equals(modId) || container.getContainingMod().isPresent()) {
            continue;
         }

         for (ModDependency dependency : container.getMetadata().getDependencies()) {
            if (dependency.getKind() == ModDependency.Kind.DEPENDS && dependency.getModId().equals(modId)) {
               names.add(container.getMetadata().getName());
               break;
            }
         }
      }

      return names;
   }

   // ---- installing -------------------------------------------------------------------------------------------

   public Job job(String projectId) {
      return this.jobs.get(projectId);
   }

   /** Mods installed this session that are not running yet (they load next start). */
   public int pendingRestart() {
      int count = 0;
      for (Path path : this.installer.installedThisSession()) {
         if (path.getParent() != null && path.getParent().equals(this.installer.modsFolder().toAbsolutePath().normalize()) && Files.exists(path)) {
            count++;
         }
      }

      return count;
   }

   /**
    * Starts installing every step of {@code plan} (unless a job for {@code key} is already running). With
    * {@code replacing} set (an update), that older file goes to the trash once the new one has been verified.
    */
   public Job install(String key, String title, ProjectType type, DependencyPlan plan, Path replacing) {
      Job existing = this.jobs.get(key);
      if (existing != null && !existing.done) {
         return existing;
      }

      Job job = new Job(type, title);
      this.jobs.put(key, job);
      long totalBytes = 0L;
      for (DependencyPlan.Step step : plan.installs()) {
         totalBytes += Math.max(1L, step.version().primaryFile().size());
      }

      long total = totalBytes;
      this.client.async(() -> {
         long before = 0L;
         Path old = replacing;
         try {
            for (DependencyPlan.Step step : plan.installs()) {
               Modrinth.File file = step.version().primaryFile();
               long base = before;
               job.message = "Downloading " + step.title();
               ModInstaller.Placed placed = this.installer.install(type, file, old, bytes -> job.progress = Math.min(1.0F, (float)(base + bytes) / total));
               old = null;
               synchronized (job) {
                  job.placed.add(placed.path());
               }

               before += Math.max(1L, file.size());
               job.progress = Math.min(1.0F, (float)before / total);
            }

            job.message = type.needsRestart() ? "Installed. Loads next time you start Minecraft." : "Downloaded.";
         } catch (IOException e) {
            job.failed = true;
            job.message = e.getMessage() == null ? "Install failed" : e.getMessage();
         } catch (RuntimeException e) {
            job.failed = true;
            job.message = "Install failed: " + e.getClass().getSimpleName();
            Gemini.LOGGER.warn("Mod install failed", e);
         } finally {
            if (type == ProjectType.MOD) {
               this.folderChanged();
            }

            job.done = true;
         }

         return null;
      });
      return job;
   }
}
