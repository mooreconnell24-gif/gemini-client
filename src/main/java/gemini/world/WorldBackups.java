package gemini.world;

import com.mojang.blaze3d.Blaze3D;
import gemini.Gemini;
import gemini.ui.HudOverlay;
import gemini.ui.SandboxUi;
import gemini.util.ClientThread;
import gemini.util.SingleplayerGate;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * World backups: zips the open single-player world into {@code backups/gemini/<world folder>/}, keeps the newest few,
 * and restores a backup into a new world folder (never over an existing one).
 *
 * <p>Order of a backup: the integrated server saves and flushes everything on its own thread and then stops writing
 * world files (vanilla's {@code save-off} flag on every dimension, the same one {@code /save-off} sets); the copy
 * runs on a background thread; afterwards the flags are put back as they were. The game keeps running throughout:
 * only the server's one flush waits for the disk. If the world is closed while the copy is still running, the server
 * writes its final save into the files being copied, so that backup is deleted rather than kept half-consistent.
 */
public final class WorldBackups {
   private static final String TITLE = "World backup";
   private static final long REPORT_EVERY_MS = 700L;
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Gemini-World-Backup");
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
   });
   private static volatile WorldBackups.Job current;

   private WorldBackups() {
   }

   public enum Kind {
      BACKUP,
      RESTORE
   }

   /** One backup or restore. Fields are written by the worker thread and read by screens and the module. */
   public static final class Job {
      public final Kind kind;
      public final String world;
      public final WorldZip.Progress progress = new WorldZip.Progress();
      private volatile boolean finished;
      private volatile boolean failed;
      private volatile String outcome = "";
      private volatile long lastReport;

      Job(Kind kind, String world) {
         this.kind = kind;
         this.world = world;
      }

      public boolean finished() {
         return this.finished;
      }

      public boolean failed() {
         return this.failed;
      }

      /** What happened, once finished: where the backup went, or why it failed. */
      public String outcome() {
         return this.outcome;
      }

      void finish(boolean failed, String outcome) {
         this.failed = failed;
         this.outcome = outcome;
         this.finished = true;
      }
   }

   /** A backup on disk. {@code world} is the world's folder name; the two texts are formatted once, for lists. */
   public record Entry(String world, Path file, String name, long size, String shownTime, String sizeText) {
      static WorldBackups.Entry of(String world, Path file, String name, long size) {
         return new WorldBackups.Entry(world, file, name, size, BackupNames.shownTime(name), size < 0L ? "" : BackupNames.size(size));
      }
   }

   /** The folder that holds every world's backups. */
   public static Path root() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("backups").resolve("gemini");
   }

   /** The latest job (running or finished), or null if none ran this session. */
   public static WorldBackups.Job current() {
      return current;
   }

   public static boolean busy() {
      WorldBackups.Job job = current;
      return job != null && !job.finished();
   }

   /** The folder name of the open single-player world, or null outside one. */
   public static String openWorldFolder() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server == null) {
         return null;
      } else {
         Path dir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
         return dir.getFileName() == null ? null : dir.getFileName().toString();
      }
   }

   /**
    * Starts a backup of the open single-player world, keeping the newest {@code keep} backups of it. Call on the client
    * thread. Returns the job, or null (after telling the user why) when no backup could be started.
    */
   public static WorldBackups.Job backUp(int keep, boolean announce) {
      Minecraft client = Minecraft.getInstance();
      IntegratedServer server = client.getSingleplayerServer();
      if (server == null || !SingleplayerGate.allowed()) {
         SandboxUi.notify(client, TITLE, "Open a single-player world to back it up.", HudOverlay.Severity.WARNING);
         return null;
      } else if (busy()) {
         SandboxUi.notify(client, TITLE, "A backup or restore is already running.", HudOverlay.Severity.WARNING);
         return null;
      }

      Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
      String folder = worldDir.getFileName() == null ? "World" : worldDir.getFileName().toString();
      String levelName = server.getWorldData().getLevelName();
      WorldBackups.Job job = new WorldBackups.Job(Kind.BACKUP, levelName);
      current = job;
      if (announce) {
         SandboxUi.notify(client, TITLE, "Saving " + levelName + "...", HudOverlay.Severity.INFO);
      }

      server.execute(() -> {
         List<ServerLevel> paused = new ArrayList<>();
         try {
            server.saveEverything(true, true, true);
            for (ServerLevel level : server.getAllLevels()) {
               if (!level.noSave) {
                  level.noSave = true;
                  paused.add(level);
               }
            }
         } catch (RuntimeException e) {
            resumeSaving(paused);
            Gemini.LOGGER.error("World backup: saving before the copy failed", e);
            fail(job, "Saving the world failed: " + e.getClass().getSimpleName());
            return;
         }

         IO.execute(() -> copy(job, server, worldDir, folder, keep, paused, announce));
      });
      return job;
   }

   private static void copy(WorldBackups.Job job, IntegratedServer server, Path worldDir, String folder, int keep, List<ServerLevel> paused, boolean announce) {
      long started = System.nanoTime();
      Path dir = root().resolve(BackupNames.safeFolder(folder));
      Path target = null;
      try {
         LocalDateTime now = LocalDateTime.now();
         for (int attempt = 1; target == null || Files.exists(target); attempt++) {
            target = dir.resolve(BackupNames.fileName(now, attempt));
         }

         if (announce) {
            job.progress.listen(() -> report(job));
         }

         long size = WorldZip.zip(worldDir, target, folder, job.progress);
         if (!server.isRunning()) {
            Files.deleteIfExists(target);
            fail(job, "The world was closed during the backup, so the copy was deleted. Back up again after loading it.");
            return;
         }

         int removed = rotate(dir, keep);
         long seconds = Math.max(1L, (System.nanoTime() - started) / 1_000_000_000L);
         String outcome = BackupNames.size(size) + " in " + seconds + " s" + (removed > 0 ? ", " + removed + " old removed" : "");
         job.finish(false, outcome);
         ClientThread.run(() -> SandboxUi.notify(Minecraft.getInstance(), TITLE, "Saved: " + outcome, HudOverlay.Severity.SUCCESS));
      } catch (IOException | RuntimeException e) {
         Gemini.LOGGER.error("World backup failed", e);
         if (target != null) {
            try {
               Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
         }

         fail(job, "The backup failed: " + message(e));
      } finally {
         server.execute(() -> resumeSaving(paused));
      }
   }

   private static void resumeSaving(List<ServerLevel> paused) {
      for (ServerLevel level : paused) {
         level.noSave = false;
      }
   }

   /** Deletes this client's oldest backups in {@code dir} beyond {@code keep}; returns how many went. */
   private static int rotate(Path dir, int keep) throws IOException {
      List<String> names = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
         for (Path path : stream) {
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
               names.add(path.getFileName().toString());
            }
         }
      }

      int removed = 0;
      for (String name : BackupNames.toDelete(names, keep)) {
         if (Files.deleteIfExists(dir.resolve(name))) {
            removed++;
         }
      }

      return removed;
   }

   private static void report(WorldBackups.Job job) {
      long now = System.currentTimeMillis();
      if (now - job.lastReport >= REPORT_EVERY_MS && !job.finished()) {
         job.lastReport = now;
         String body = (job.kind == Kind.BACKUP ? "Copying " : "Restoring ") + job.world + ": " + WorldZip.percent(job.progress.fraction());
         ClientThread.run(() -> {
            if (!job.finished()) {
               SandboxUi.notify(Minecraft.getInstance(), TITLE, body, HudOverlay.Severity.INFO);
            }
         });
      }
   }

   private static void fail(WorldBackups.Job job, String why) {
      job.finish(true, why);
      ClientThread.run(() -> SandboxUi.notify(Minecraft.getInstance(), TITLE, why, HudOverlay.Severity.ERROR));
   }

   private static String message(Exception e) {
      String text = e.getMessage();
      return text == null || text.isBlank() ? e.getClass().getSimpleName() : text;
   }

   /** Lists every backup on a background thread and hands the list (by world, newest first) to {@code done} on the client thread. */
   public static void list(Consumer<List<WorldBackups.Entry>> done) {
      Path root = root();
      IO.execute(() -> {
         List<WorldBackups.Entry> entries = new ArrayList<>();
         if (Files.isDirectory(root)) {
            List<Path> worlds = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
               for (Path path : stream) {
                  if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                     worlds.add(path);
                  }
               }
            } catch (IOException e) {
               Gemini.LOGGER.warn("Could not list world backups", e);
            }

            worlds.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
            for (Path world : worlds) {
               List<String> names = new ArrayList<>();
               try (DirectoryStream<Path> stream = Files.newDirectoryStream(world, "*.zip")) {
                  for (Path path : stream) {
                     names.add(path.getFileName().toString());
                  }
               } catch (IOException e) {
                  Gemini.LOGGER.warn("Could not list backups of {}", world, e);
               }

               for (String name : BackupNames.newestFirst(names)) {
                  Path file = world.resolve(name);
                  long size;
                  try {
                     size = Files.size(file);
                  } catch (IOException e) {
                     size = -1L;
                  }

                  entries.add(WorldBackups.Entry.of(world.getFileName().toString(), file, name, size));
               }
            }
         }

         ClientThread.run(() -> done.accept(entries));
      });
   }

   /**
    * Restores {@code entry} into a new world folder next to the other saves, named after the world and the time of the
    * backup, and gives it a matching name in the world list. Never touches an existing folder. Works with or without a
    * world open, while Gemini is in sandbox mode.
    */
   public static WorldBackups.Job restore(WorldBackups.Entry entry) {
      Minecraft client = Minecraft.getInstance();
      if (!SingleplayerGate.sandboxMode()) {
         SandboxUi.notify(client, TITLE, "Gemini is switched off.", HudOverlay.Severity.WARNING);
         return null;
      } else if (busy()) {
         SandboxUi.notify(client, TITLE, "A backup or restore is already running.", HudOverlay.Severity.WARNING);
         return null;
      }

      LevelStorageSource source = client.getLevelSource();
      Path base = source.getBaseDir();
      WorldBackups.Job job = new WorldBackups.Job(Kind.RESTORE, entry.world());
      current = job;
      SandboxUi.notify(client, TITLE, "Restoring " + entry.world() + " (" + entry.shownTime() + ")...", HudOverlay.Severity.INFO);
      IO.execute(() -> {
         try {
            LocalDateTime taken = BackupNames.time(entry.name());
            String folder = BackupNames.restoredFolder(entry.world(), taken, name -> Files.exists(base.resolve(name), LinkOption.NOFOLLOW_LINKS));
            job.progress.listen(() -> report(job));
            WorldZip.unzip(entry.file(), base.resolve(folder), job.progress);
            String shown = folder;
            try (LevelStorageSource.LevelStorageAccess access = source.createAccess(folder)) {
               String original;
               try {
                  original = access.fixAndGetSummary().getLevelName();
               } catch (IOException | RuntimeException e) {
                  original = entry.world();
               }

               shown = BackupNames.restoredDisplayName(original, taken);
               access.renameLevel(shown);
            } catch (IOException | RuntimeException e) {
               // The files are restored; only the name in the world list stays the original one.
               Gemini.LOGGER.warn("Restored world {} could not be renamed", folder, e);
            }

            String outcome = "Restored as \"" + shown + "\" in your world list";
            job.finish(false, outcome);
            ClientThread.run(() -> SandboxUi.notify(Minecraft.getInstance(), TITLE, outcome, HudOverlay.Severity.SUCCESS));
         } catch (IOException | RuntimeException e) {
            Gemini.LOGGER.error("Restoring {} failed", entry.file(), e);
            fail(job, "Restore failed: " + message(e));
         }
      });
      return job;
   }

   /** Opens the backups folder (or one world's) in the system file browser, creating it first. */
   public static void openFolder(String world) {
      Path dir = world == null ? root() : root().resolve(BackupNames.safeFolder(world));
      try {
         Files.createDirectories(dir);
         Blaze3D.openPath(dir);
      } catch (IOException | RuntimeException e) {
         SandboxUi.notify(Minecraft.getInstance(), TITLE, "Could not open " + dir, HudOverlay.Severity.ERROR);
      }
   }
}
