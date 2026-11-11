package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigWriterTest {
   @TempDir
   Path dir;

   private final List<Path> failed = new CopyOnWriteArrayList<>();
   private final ConfigWriter writer = new ConfigWriter((path, error) -> this.failed.add(path));

   @Test
   void theLastSubmittedVersionIsWhatEndsUpOnDisk() throws IOException {
      Path target = this.dir.resolve("config.json");
      for (int i = 0; i < 500; i++) {
         this.writer.submit(target, "{\"n\":" + i + "}", true);
      }

      assertTrue(this.writer.awaitIdle(10000L));
      assertEquals("{\"n\":499}", Files.readString(target));
      assertTrue(this.failed.isEmpty());
      assertFalse(this.writer.hasFailures());
   }

   @Test
   void filesAreWrittenIndependently() throws IOException {
      Path a = this.dir.resolve("a.json");
      Path b = this.dir.resolve("profiles").resolve("b.json");
      this.writer.submit(a, "{\"file\":\"a\"}", false);
      this.writer.submit(b, "{\"file\":\"b\"}", false);
      assertTrue(this.writer.awaitIdle(10000L));
      assertEquals("{\"file\":\"a\"}", Files.readString(a));
      assertEquals("{\"file\":\"b\"}", Files.readString(b));
   }

   @Test
   void aFailedWriteIsReportedKeptAndRetried() throws IOException {
      // The parent "directory" is a regular file, so the write cannot succeed until that is fixed.
      Path parent = this.dir.resolve("blocked");
      Files.writeString(parent, "in the way");
      Path target = parent.resolve("config.json");
      this.writer.submit(target, "{\"kept\":true}", false);
      assertTrue(this.writer.awaitIdle(10000L));
      assertEquals(List.of(target), this.failed);
      assertTrue(this.writer.hasFailures());

      Files.delete(parent);
      assertEquals(1, this.writer.retryFailed());
      assertTrue(this.writer.awaitIdle(10000L));
      assertFalse(this.writer.hasFailures());
      assertEquals("{\"kept\":true}", Files.readString(target), "the content from the failed attempt must not be lost");
   }

   @Test
   void aNewerSaveReplacesAFailedOne() throws IOException {
      Path parent = this.dir.resolve("blocked");
      Files.writeString(parent, "in the way");
      Path target = parent.resolve("config.json");
      this.writer.submit(target, "{\"old\":true}", false);
      assertTrue(this.writer.awaitIdle(10000L));
      Files.delete(parent);
      this.writer.submit(target, "{\"new\":true}", false);
      assertTrue(this.writer.awaitIdle(10000L));
      assertFalse(this.writer.hasFailures());
      assertEquals(0, this.writer.retryFailed());
      assertEquals("{\"new\":true}", Files.readString(target));
   }

   @Test
   void idleWriterReportsIdleImmediately() {
      assertTrue(this.writer.awaitIdle(0L));
   }
}
