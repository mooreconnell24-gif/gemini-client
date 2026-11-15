package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFilesTest {
   @TempDir
   Path dir;

   private static List<String> names(Path dir) throws IOException {
      try (Stream<Path> files = Files.list(dir)) {
         return files.map(path -> path.getFileName().toString()).sorted().toList();
      }
   }

   @Test
   void writesTheFileAndLeavesNoTemporaryFilesBehind() throws IOException {
      Path target = this.dir.resolve("config.json");
      JsonFiles.writeAtomic(target, "{\"a\":1}", true);
      assertEquals("{\"a\":1}", Files.readString(target, StandardCharsets.UTF_8));
      assertEquals(List.of("config.json"), names(this.dir));
   }

   @Test
   void keepsThePreviousVersionAsBackup() throws IOException {
      Path target = this.dir.resolve("config.json");
      JsonFiles.writeAtomic(target, "{\"version\":1}", true);
      JsonFiles.writeAtomic(target, "{\"version\":2}", true);
      assertEquals("{\"version\":2}", Files.readString(target));
      assertEquals("{\"version\":1}", Files.readString(JsonFiles.backupOf(target)));
      assertEquals(List.of("config.json", "config.json.bak"), names(this.dir));
   }

   @Test
   void backupIsOptional() throws IOException {
      Path target = this.dir.resolve("profile.json");
      JsonFiles.writeAtomic(target, "{}", false);
      JsonFiles.writeAtomic(target, "{\"b\":2}", false);
      assertFalse(Files.exists(JsonFiles.backupOf(target)));
   }

   @Test
   void createsMissingDirectories() throws IOException {
      Path target = this.dir.resolve("profiles").resolve("deep").resolve("pvp.json");
      JsonFiles.writeAtomic(target, "{}", false);
      assertTrue(Files.isRegularFile(target));
   }

   @Test
   void aFailedWriteLeavesTheOldFileUntouched() throws IOException {
      Path target = this.dir.resolve("config.json");
      JsonFiles.writeAtomic(target, "{\"good\":true}", true);
      // A directory where the file should go makes the final rename fail after the temp file was written.
      Path blocked = this.dir.resolve("blocked.json");
      Files.createDirectories(blocked.resolve("child"));
      assertThrows(IOException.class, () -> JsonFiles.writeAtomic(blocked, "{\"new\":true}", false));
      assertEquals("{\"good\":true}", Files.readString(target));
      assertEquals(List.of("blocked.json", "config.json"), names(this.dir), "the temporary file must be cleaned up after a failure");
   }

   @Test
   void readsAnObjectAndRejectsEverythingElse() throws IOException {
      Path good = this.dir.resolve("good.json");
      Files.writeString(good, "{\"modules\":{}}");
      assertTrue(JsonFiles.read(good).has("modules"));
      for (String broken : new String[]{"", "   ", "[1,2]", "42", "{\"cut\": "}) {
         Path file = this.dir.resolve("broken.json");
         Files.writeString(file, broken);
         assertThrows(RuntimeException.class, () -> JsonFiles.read(file), "should reject: " + broken);
      }
   }

   @Test
   void fallsBackToTheBackupWhenTheMainFileIsBroken() throws IOException {
      Path target = this.dir.resolve("config.json");
      JsonFiles.writeAtomic(target, "{\"version\":1}", true);
      JsonFiles.writeAtomic(target, "{\"version\":2}", true);
      Files.writeString(target, "{\"version\": 3, \"trunc");
      JsonFiles.Loaded loaded = JsonFiles.readWithBackup(target);
      assertNotNull(loaded);
      assertTrue(loaded.fromBackup());
      assertNotNull(loaded.mainFailure());
      assertEquals(1, loaded.json().get("version").getAsInt());
   }

   @Test
   void fallsBackToTheBackupWhenTheMainFileIsMissing() throws IOException {
      Path target = this.dir.resolve("config.json");
      Files.writeString(JsonFiles.backupOf(target), "{\"version\":7}");
      JsonFiles.Loaded loaded = JsonFiles.readWithBackup(target);
      assertTrue(loaded.fromBackup());
      assertNull(loaded.mainFailure());
      assertEquals(7, loaded.json().get("version").getAsInt());
   }

   @Test
   void aHealthyMainFileWinsOverTheBackup() throws IOException {
      Path target = this.dir.resolve("config.json");
      Files.writeString(target, "{\"version\":8}");
      Files.writeString(JsonFiles.backupOf(target), "{\"version\":7}");
      JsonFiles.Loaded loaded = JsonFiles.readWithBackup(target);
      assertFalse(loaded.fromBackup());
      assertEquals(8, loaded.json().get("version").getAsInt());
   }

   @Test
   void nothingOnDiskIsNotAnError() throws IOException {
      assertNull(JsonFiles.readWithBackup(this.dir.resolve("absent.json")));
   }

   @Test
   void bothUnreadableIsReported() throws IOException {
      Path target = this.dir.resolve("config.json");
      Files.writeString(target, "not json");
      Files.writeString(JsonFiles.backupOf(target), "[]");
      assertThrows(IOException.class, () -> JsonFiles.readWithBackup(target));
   }

   @Test
   void jsonRoundTripsThroughTheWriter() throws IOException {
      JsonObject root = new JsonObject();
      root.addProperty("name", "§bGemini <test> & \"quotes\"");
      root.addProperty("sandboxMode", true);
      Path target = this.dir.resolve("round.json");
      JsonFiles.writeAtomic(target, root, false);
      assertEquals(root, JsonFiles.read(target));
   }
}
