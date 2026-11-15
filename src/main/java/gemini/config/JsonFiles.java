package gemini.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Reading and crash-safe writing of JSON files.
 *
 * <p>{@link #writeAtomic} never leaves a half-written file where a reader could find it: the text goes to a
 * uniquely named temporary file in the same directory, is forced to disk, and only then renamed over the target.
 * A crash, a full disk or a power cut at any point leaves either the complete old file or the complete new one.
 * The previous version is kept next to the target as {@code <name>.bak}, and {@link #readWithBackup} falls back
 * to it when the main file is missing or unreadable.
 */
public final class JsonFiles {
   public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final String BACKUP_SUFFIX = ".bak";

   private JsonFiles() {
   }

   public static Path backupOf(Path target) {
      return target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
   }

   /** Writes the text so that the target only ever holds a complete file. With {@code keepBackup} the old file is preserved as {@code .bak}. */
   public static void writeAtomic(Path target, String content, boolean keepBackup) throws IOException {
      Path absolute = target.toAbsolutePath();
      Path dir = absolute.getParent();
      Files.createDirectories(dir);
      Path temp = Files.createTempFile(dir, absolute.getFileName().toString() + ".", ".tmp");

      try {
         try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
               channel.write(buffer);
            }

            // Without this a crash shortly after the rename can still surface an empty file on some file systems.
            channel.force(true);
         }

         if (keepBackup && Files.isRegularFile(absolute)) {
            try {
               Files.copy(absolute, backupOf(absolute), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
               // A missing backup must never stop the real save.
            }
         }

         try {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   public static void writeAtomic(Path target, JsonElement json, boolean keepBackup) throws IOException {
      writeAtomic(target, GSON.toJson(json), keepBackup);
   }

   /** Parses the file as one JSON object. Empty files, arrays and plain values are rejected like broken syntax. */
   public static JsonObject read(Path path) throws IOException {
      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         JsonElement element = GSON.fromJson(reader, JsonElement.class);
         if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(path.getFileName() + " does not contain a JSON object");
         }

         return element.getAsJsonObject();
      }
   }

   public static JsonObject parse(String text) {
      JsonElement element = GSON.fromJson(text, JsonElement.class);
      if (element == null || !element.isJsonObject()) {
         throw new JsonParseException("not a JSON object");
      }

      return element.getAsJsonObject();
   }

   /**
    * Reads the file, or its {@code .bak} when the file is missing or broken. Returns null when neither exists;
    * throws the main file's error when both are unreadable.
    */
   public static Loaded readWithBackup(Path path) throws IOException {
      Path backup = backupOf(path);
      Exception failure = null;
      if (Files.isRegularFile(path)) {
         try {
            return new Loaded(read(path), false, null);
         } catch (IOException | RuntimeException e) {
            failure = e;
         }
      }

      if (Files.isRegularFile(backup)) {
         try {
            return new Loaded(read(backup), true, failure);
         } catch (IOException | RuntimeException e) {
            if (failure == null) {
               failure = e;
            }
         }
      }

      if (failure == null) {
         return null;
      } else if (failure instanceof IOException io) {
         throw io;
      } else {
         throw new IOException(failure.getMessage(), failure);
      }
   }

   /** A parsed file, whether it came from the backup, and why the main file was skipped. */
   public record Loaded(JsonObject json, boolean fromBackup, Exception mainFailure) {
   }
}
