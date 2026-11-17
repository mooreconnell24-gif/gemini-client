package gemini.mods;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Puts Modrinth files into the game folder, and disables or removes mods. The rules:
 * <ul>
 *    <li>files come only from {@link SafeFiles#CDN_HOST}, only as {@code .jar} (mods) or {@code .zip} (packs), under a
 *    sanitised name directly inside the target folder;</li>
 *    <li>the download goes to a hidden temporary file, its SHA-512 is checked against the API's hash, and only then is
 *    it renamed into place in the same folder;</li>
 *    <li>nothing is ever overwritten: an existing file with the same name is either the same file (nothing to do) or
 *    the install is refused;</li>
 *    <li>Disable renames {@code x.jar} to {@code x.jar.disabled}; Remove moves the file into {@code mods/.gemini-trash};
 *    nothing is deleted. Protected files (Gemini's own jar, Fabric API, the loader) are refused.</li>
 * </ul>
 * Mods only load when the game starts, so every change to the mods folder takes effect on the next start.
 *
 * <p>Blocking I/O: call from a worker thread, never the client thread.
 */
public final class ModInstaller {
   /** Largest file accepted. */
   public static final long MAX_FILE = 512L << 20;
   private final Path gameDir;
   private final Downloader downloader;
   private final Predicate<Path> protectedFile;
   private final Set<Path> installedThisSession = ConcurrentHashMap.newKeySet();

   /** Fetches {@code uri} into {@code target}, reporting bytes received; refuses more than {@code maxBytes}. */
   @FunctionalInterface
   public interface Downloader {
      void download(URI uri, Path target, long maxBytes, LongConsumer progress) throws IOException;
   }

   /** A refused or failed install, with a message meant for the user. */
   public static final class Refused extends IOException {
      public Refused(String message) {
         super(message);
      }
   }

   /** Where a file ended up and whether it was already there. */
   public record Placed(Path path, boolean alreadyThere) {
   }

   public ModInstaller(Path gameDir, Downloader downloader, Predicate<Path> protectedFile) {
      this.gameDir = gameDir;
      this.downloader = downloader;
      this.protectedFile = protectedFile;
   }

   public Path folder(ProjectType type) {
      return this.gameDir.resolve(type.folder);
   }

   public Path modsFolder() {
      return this.folder(ProjectType.MOD);
   }

   /** Files this session put into a folder (they are not running yet if they are mods). */
   public Set<Path> installedThisSession() {
      return Set.copyOf(this.installedThisSession);
   }

   /** Downloads, verifies and places {@code file}. */
   public Placed install(ProjectType type, Modrinth.File file, LongConsumer progress) throws IOException {
      return this.install(type, file, null, progress);
   }

   /**
    * Like {@link #install(ProjectType, Modrinth.File, LongConsumer)}, but once the new file is downloaded and verified
    * {@code replacing} (an older version of the same mod) is moved to the trash first.
    */
   public Placed install(ProjectType type, Modrinth.File file, Path replacing, LongConsumer progress) throws IOException {
      if (!SafeFiles.allowedDownload(file.url())) {
         throw new Refused("Only files from " + SafeFiles.CDN_HOST + " are installed");
      }

      String name = SafeFiles.sanitizeFilename(file.filename(), type.extension);
      if (name == null) {
         throw new Refused("Not a " + type.extension + " file: " + file.filename());
      }

      if (file.sha512() == null || file.sha512().length() != 128) {
         throw new Refused("Modrinth gave no SHA-512 hash for " + name);
      }

      if (file.size() > MAX_FILE) {
         throw new Refused(name + " is too large");
      }

      Path folder = this.folder(type);
      Files.createDirectories(folder);
      Path target = folder.resolve(name);
      if (!SafeFiles.directChild(folder, target)) {
         throw new Refused("Bad file name: " + name);
      }

      Path old = replacing == null ? null : replacing.toAbsolutePath().normalize();
      boolean replacesSameName = old != null && old.equals(target.toAbsolutePath().normalize());
      if (Files.exists(target) && !replacesSameName) {
         if (SafeFiles.sameHash(file.sha512(), SafeFiles.sha512(target))) {
            return new Placed(target, true);
         }

         throw new Refused("A different " + name + " is already in " + type.folder + "; Gemini does not replace files it did not put there");
      }

      if (type == ProjectType.MOD && Files.exists(folder.resolve(name + InstalledMods.DISABLED_SUFFIX))) {
         throw new Refused(name + " is already in mods but disabled; enable it in the Installed tab");
      }

      Path temp = folder.resolve(".gemini-" + UUID.randomUUID() + ".part");
      try {
         long limit = file.size() > 0L ? file.size() : MAX_FILE;
         this.downloader.download(URI.create(file.url()), temp, limit, progress == null ? bytes -> {} : progress);
         if (file.size() > 0L && Files.size(temp) != file.size()) {
            throw new Refused(name + " did not download completely");
         }

         if (!SafeFiles.sameHash(file.sha512(), SafeFiles.sha512(temp))) {
            throw new Refused(name + " failed its SHA-512 check and was not installed");
         }

         if (old != null) {
            this.trash(old);
         }

         // Same folder, so this is a single rename. Without REPLACE_EXISTING it throws when a file appeared under
         // that name meanwhile (ATOMIC_MOVE is not used: on Linux it would silently replace that file).
         try {
            Files.move(temp, target);
         } catch (FileAlreadyExistsException e) {
            throw new Refused(name + " appeared in " + type.folder + " during the download; it was left alone");
         }
      } finally {
         Files.deleteIfExists(temp);
      }

      this.installedThisSession.add(target.toAbsolutePath().normalize());
      return new Placed(target, false);
   }

   /** Renames {@code jar} to {@code jar.disabled}; Fabric skips it from the next start. */
   public Path disable(Path jar) throws IOException {
      Path file = this.checkModFile(jar);
      String name = file.getFileName().toString();
      if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
         throw new Refused(name + " is not an enabled mod");
      }

      Path target = file.resolveSibling(name + InstalledMods.DISABLED_SUFFIX);
      if (Files.exists(target)) {
         throw new Refused(target.getFileName() + " already exists");
      }

      Files.move(file, target);
      return target;
   }

   /** Renames {@code x.jar.disabled} back to {@code x.jar}. */
   public Path enable(Path disabledJar) throws IOException {
      Path file = this.checkModFile(disabledJar);
      String name = file.getFileName().toString();
      if (!name.toLowerCase(Locale.ROOT).endsWith(".jar" + InstalledMods.DISABLED_SUFFIX)) {
         throw new Refused(name + " is not a disabled mod");
      }

      Path target = file.resolveSibling(name.substring(0, name.length() - InstalledMods.DISABLED_SUFFIX.length()));
      if (Files.exists(target)) {
         throw new Refused(target.getFileName() + " already exists");
      }

      Files.move(file, target);
      return target;
   }

   /** Moves a mod file into {@code mods/.gemini-trash} (never deletes it). */
   public Path trash(Path modFile) throws IOException {
      Path file = this.checkModFile(modFile);
      Path trash = this.modsFolder().resolve(InstalledMods.TRASH);
      Files.createDirectories(trash);
      String name = file.getFileName().toString();
      Path target = trash.resolve(name);
      for (int i = 1; Files.exists(target); i++) {
         target = trash.resolve(i + "-" + name);
      }

      Files.move(file, target);
      this.installedThisSession.remove(file);
      return target;
   }

   private Path checkModFile(Path path) throws IOException {
      Path file = path.toAbsolutePath().normalize();
      if (!SafeFiles.directChild(this.modsFolder(), file) || !Files.isRegularFile(file)) {
         throw new Refused(path.getFileName() + " is not in the mods folder");
      }

      String name = file.getFileName().toString();
      Path jar = name.endsWith(InstalledMods.DISABLED_SUFFIX) ? file.resolveSibling(name.substring(0, name.length() - InstalledMods.DISABLED_SUFFIX.length())) : file;
      if (this.protectedFile.test(file) || this.protectedFile.test(jar)) {
         throw new Refused(name + " is needed for Gemini to start, so it is left alone");
      }

      return file;
   }

   /** Names for a list of steps, e.g. for "Installs Mod Menu and Text Placeholder API". */
   public static String joinNames(List<String> names) {
      if (names.isEmpty()) {
         return "";
      } else if (names.size() == 1) {
         return names.get(0);
      }

      return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
   }
}
