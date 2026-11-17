package gemini.world;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names for world backups, which backups to delete, and names for restored worlds. Pure string logic, no files.
 *
 * <p>A backup is {@code backups/gemini/<world folder>/<yyyy-MM-dd_HH-mm-ss>.zip}; two backups in the same second get
 * {@code -2}, {@code -3}... Only files with exactly that shape are ever deleted by rotation, so a zip the user renamed
 * or dropped into the folder is left alone.
 */
public final class BackupNames {
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
   private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.ROOT);
   private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
   private static final Pattern NAME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?:-(\\d{1,4}))?\\.zip");
   /** Characters Windows refuses in a file name, plus the separators every system refuses. */
   private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
   private static final int MAX_FOLDER = 60;

   private BackupNames() {
   }

   /** The file name for a backup taken at {@code time}; {@code attempt} 1 is the plain name, 2 and up add a suffix. */
   public static String fileName(LocalDateTime time, int attempt) {
      String stamp = STAMP.format(time);
      return attempt <= 1 ? stamp + ".zip" : stamp + "-" + attempt + ".zip";
   }

   /** Whether {@code name} is a backup this client wrote (and so may delete during rotation). */
   public static boolean isBackup(String name) {
      return name != null && NAME.matcher(name).matches();
   }

   /** When a backup was taken, read from its file name, or null for any other file. */
   public static LocalDateTime time(String name) {
      if (name == null) {
         return null;
      }

      Matcher m = NAME.matcher(name);
      if (!m.matches()) {
         return null;
      }

      try {
         return LocalDateTime.parse(m.group(1), STAMP);
      } catch (DateTimeParseException e) {
         return null;
      }
   }

   /** Sort key: newest first, and within one second the higher suffix first. */
   private static final Comparator<String> NEWEST_FIRST = Comparator.<String, String>comparing(name -> {
      Matcher m = NAME.matcher(name);
      m.matches();
      String suffix = m.group(2) == null ? "0000" : String.format(Locale.ROOT, "%04d", Integer.parseInt(m.group(2)));
      return m.group(1) + suffix;
   }).reversed();

   /** Backups among {@code names}, newest first; other files are left out. */
   public static List<String> newestFirst(Collection<String> names) {
      List<String> backups = new ArrayList<>();
      for (String name : names) {
         if (isBackup(name) && time(name) != null) {
            backups.add(name);
         }
      }

      backups.sort(NEWEST_FIRST);
      return backups;
   }

   /**
    * The backups to delete so that only the newest {@code keep} remain (at least one is always kept). Files that are not
    * this client's backups are never returned.
    */
   public static List<String> toDelete(Collection<String> names, int keep) {
      List<String> sorted = newestFirst(names);
      int kept = Math.max(1, keep);
      return sorted.size() <= kept ? List.of() : List.copyOf(sorted.subList(kept, sorted.size()));
   }

   /** A world name made safe to use as one folder name: no separators or reserved characters, not "." or "..", not empty. */
   public static String safeFolder(String name) {
      String cleaned = UNSAFE.matcher(name == null ? "" : name).replaceAll("_").strip();
      while (cleaned.endsWith(".")) {
         cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
      }

      while (cleaned.startsWith(".")) {
         cleaned = cleaned.substring(1).strip();
      }

      if (cleaned.length() > MAX_FOLDER) {
         cleaned = cleaned.substring(0, MAX_FOLDER).strip();
      }

      return cleaned.isEmpty() ? "World" : cleaned;
   }

   /**
    * The folder for a world restored from a backup taken at {@code taken}: "{@code <world> (backup 2026-11-17 14-03)}",
    * with " 2", " 3"... added until {@code taken} reports the name is free.
    */
   public static String restoredFolder(String world, LocalDateTime taken, Predicate<String> isTaken) {
      String base = safeFolder(world) + " (backup " + (taken == null ? "restored" : SHORT.format(taken)) + ")";
      String candidate = base;
      for (int n = 2; isTaken.test(candidate); n++) {
         candidate = base + " " + n;
         if (n > 9999) {
            throw new IllegalStateException("no free folder name for " + base);
         }
      }

      return candidate;
   }

   /** The name shown in the world list for a restored world. */
   public static String restoredDisplayName(String levelName, LocalDateTime taken) {
      String name = levelName == null || levelName.isBlank() ? "World" : levelName.strip();
      return name + " (backup " + (taken == null ? "restored" : SHOWN.format(taken)) + ")";
   }

   /** A backup's time for lists: "2026-11-17 14:03", or the file name when it has no time. */
   public static String shownTime(String fileName) {
      LocalDateTime time = time(fileName);
      return time == null ? fileName : SHOWN.format(time);
   }

   /** A size in bytes as "812 KB", "12.3 MB" or "1.42 GB", independent of the system locale. */
   public static String size(long bytes) {
      if (bytes < 1024L) {
         return Math.max(0L, bytes) + " B";
      } else if (bytes < 1024L * 1024L) {
         return Math.round(bytes / 1024.0) + " KB";
      } else if (bytes < 1024L * 1024L * 1024L) {
         return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
      } else {
         return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
      }
   }
}
