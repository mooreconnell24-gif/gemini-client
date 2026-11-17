package gemini.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Zips a world folder into a backup and unpacks a backup into a new folder. Plain file work with no game classes, so
 * it runs on a background thread and in unit tests.
 *
 * <p>Backups hold one top-level folder (the world's folder name) like vanilla's own "Make Backup". Unpacking checks
 * every entry before writing anything: an entry that would land outside the target folder (absolute paths, drive
 * letters, ".." segments, backslash tricks) makes the whole archive refused, so a crafted zip cannot write elsewhere.
 */
public final class WorldZip {
   /** The vanilla lock file of an open world; never copied into or out of a backup. */
   public static final String LOCK_FILE = "session.lock";
   private static final int BUFFER = 64 * 1024;
   private static final long MAX_ENTRIES = 1_000_000L;

   private WorldZip() {
   }

   /** Progress shared with the thread doing the work. Read from any thread. */
   public static final class Progress {
      private volatile long done;
      private volatile long total;
      private volatile boolean cancelled;
      private volatile Runnable listener;

      public long done() {
         return this.done;
      }

      public long total() {
         return this.total;
      }

      /** 0..1, or 0 while the total is not known yet. */
      public float fraction() {
         long t = this.total;
         return t <= 0L ? 0.0F : (float)Math.min(1.0, (double)this.done / t);
      }

      public void cancel() {
         this.cancelled = true;
      }

      public boolean cancelled() {
         return this.cancelled;
      }

      /** {@code listener} runs on the working thread after every chunk written; it should be cheap and rate-limit itself. */
      public void listen(Runnable listener) {
         this.listener = listener;
      }

      void total(long total) {
         this.total = total;
      }

      void add(long bytes) {
         this.done += bytes;
         Runnable l = this.listener;
         if (l != null) {
            l.run();
         }
      }
   }

   /** Thrown when an archive is refused before anything was written. */
   public static final class UnsafeArchiveException extends IOException {
      public UnsafeArchiveException(String message) {
         super(message);
      }
   }

   /**
    * Writes {@code worldDir} into the zip {@code target}, under the top-level folder {@code rootName}. The archive is
    * written to a temporary file next to {@code target} and moved into place when complete, so a crash or a cancel never
    * leaves a half-written backup under a backup's name. Returns the size of the finished zip.
    */
   public static long zip(Path worldDir, Path target, String rootName, Progress progress) throws IOException {
      List<Path> files = new ArrayList<>();
      long total = 0L;
      try (Stream<Path> walk = Files.walk(worldDir)) {
         for (Path path : (Iterable<Path>)walk::iterator) {
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !LOCK_FILE.equals(path.getFileName().toString())) {
               files.add(path);
               try {
                  total += Files.size(path);
               } catch (NoSuchFileException ignored) {
               }
            }
         }
      }

      files.sort(Comparator.naturalOrder());
      progress.total(Math.max(1L, total));
      Files.createDirectories(target.getParent());
      Path part = target.resolveSibling(target.getFileName() + ".part");
      String root = BackupNames.safeFolder(rootName);
      byte[] buffer = new byte[BUFFER];
      boolean finished = false;
      try {
         try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(part))) {
            out.putNextEntry(new ZipEntry(root + "/"));
            out.closeEntry();
            for (Path file : files) {
               if (progress.cancelled()) {
                  throw new IOException("cancelled");
               }

               String relative = worldDir.relativize(file).toString().replace('\\', '/');
               try (InputStream in = Files.newInputStream(file)) {
                  ZipEntry entry = new ZipEntry(root + "/" + relative);
                  entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                  out.putNextEntry(entry);
                  int read;
                  while ((read = in.read(buffer)) > 0) {
                     out.write(buffer, 0, read);
                     progress.add(read);
                  }

                  out.closeEntry();
               } catch (NoSuchFileException ignored) {
                  // A file the game removed after the listing (a temporary file): nothing to keep.
               }
            }
         }

         Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         finished = true;
         return Files.size(target);
      } finally {
         if (!finished) {
            Files.deleteIfExists(part);
         }
      }
   }

   /**
    * Where entry {@code name} would be written under {@code root}, or null when it must not be written: absolute,
    * a drive letter, a ".." segment, or anything that resolves outside {@code root}. Backslashes count as separators,
    * because they are separators on Windows. {@code root} should be absolute and normalised.
    */
   public static Path safeTarget(Path root, String name) {
      if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) {
         return null;
      }

      String unified = name.replace('\\', '/');
      if (unified.startsWith("/") || unified.length() >= 2 && unified.charAt(1) == ':') {
         return null;
      }

      for (String segment : unified.split("/")) {
         if (segment.equals("..") || segment.indexOf(':') >= 0) {
            return null;
         }
      }

      Path resolved = root.resolve(unified).normalize();
      return resolved.startsWith(root) && !resolved.equals(root) ? resolved : null;
   }

   /**
    * The single top-level folder every entry of {@code names} sits in (the world folder of a backup), or "" when the
    * entries sit directly at the top or under different folders.
    */
   public static String commonRoot(List<String> names) {
      String root = null;
      for (String raw : names) {
         String name = raw.replace('\\', '/');
         int slash = name.indexOf('/');
         if (slash <= 0) {
            return "";
         }

         String first = name.substring(0, slash);
         if (first.equals(".") || first.equals("..") || first.indexOf(':') >= 0) {
            return "";
         }

         if (root == null) {
            root = first;
         } else if (!root.equals(first)) {
            return "";
         }
      }

      return root == null ? "" : root;
   }

   /**
    * Unpacks a backup into {@code dest}, which must not exist yet. The common top-level folder is dropped so the world
    * files land directly in {@code dest}. Refused (nothing written) when an entry is unsafe, when the archive has no
    * {@code level.dat} at its world root, or when it is implausibly large. On any failure the partly written folder is
    * removed again.
    */
   public static void unzip(Path zip, Path dest, Progress progress) throws IOException {
      Path target = dest.toAbsolutePath().normalize();
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
         throw new IOException("the folder " + target.getFileName() + " already exists");
      }

      try (ZipFile file = new ZipFile(zip.toFile())) {
         List<String> names = new ArrayList<>();
         long total = 0L;
         Enumeration<? extends ZipEntry> entries = file.entries();
         while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            names.add(entry.getName());
            if (names.size() > MAX_ENTRIES) {
               throw new UnsafeArchiveException("the backup has too many files");
            }

            total += Math.max(0L, entry.getSize());
         }

         String root = commonRoot(names);
         boolean hasLevel = false;
         for (String name : names) {
            String inner = strip(name, root);
            if (inner.isEmpty()) {
               continue;
            }

            if (safeTarget(target, inner) == null) {
               throw new UnsafeArchiveException("the backup contains an unsafe path: " + name);
            }

            if (inner.equals("level.dat")) {
               hasLevel = true;
            }
         }

         if (!hasLevel) {
            throw new UnsafeArchiveException("the zip is not a world backup (no level.dat)");
         }

         progress.total(Math.max(1L, total));
         boolean finished = false;
         Files.createDirectories(target);
         try {
            byte[] buffer = new byte[BUFFER];
            entries = file.entries();
            while (entries.hasMoreElements()) {
               if (progress.cancelled()) {
                  throw new IOException("cancelled");
               }

               ZipEntry entry = entries.nextElement();
               String inner = strip(entry.getName(), root);
               if (inner.isEmpty() || inner.equals(LOCK_FILE)) {
                  continue;
               }

               Path out = safeTarget(target, inner);
               if (out == null) {
                  throw new UnsafeArchiveException("the backup contains an unsafe path: " + entry.getName());
               }

               if (entry.isDirectory() || inner.endsWith("/")) {
                  Files.createDirectories(out);
                  continue;
               }

               Files.createDirectories(out.getParent());
               try (InputStream in = file.getInputStream(entry); OutputStream os = Files.newOutputStream(out)) {
                  int read;
                  while ((read = in.read(buffer)) > 0) {
                     os.write(buffer, 0, read);
                     progress.add(read);
                  }
               }
            }

            finished = true;
         } finally {
            if (!finished) {
               deleteTree(target);
            }
         }
      }
   }

   private static String strip(String name, String root) {
      String unified = name.replace('\\', '/');
      if (!root.isEmpty()) {
         if (unified.equals(root) || unified.equals(root + "/")) {
            return "";
         }

         if (unified.startsWith(root + "/")) {
            unified = unified.substring(root.length() + 1);
         }
      }

      return unified;
   }

   /** Deletes a folder and everything in it, without following links out of it. Missing folders are fine. */
   public static void deleteTree(Path dir) throws IOException {
      if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
         return;
      }

      Files.walkFileTree(dir, new SimpleFileVisitor<>() {
         @Override
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
         }

         @Override
         public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
            Files.delete(d);
            return FileVisitResult.CONTINUE;
         }
      });
   }

   /** "37%" for a progress fraction. */
   public static String percent(float fraction) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * 100.0F) + "%";
   }
}
