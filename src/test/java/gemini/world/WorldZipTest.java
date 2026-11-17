package gemini.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldZipTest {
   @TempDir
   Path temp;

   private Path world() throws IOException {
      Path world = this.temp.resolve("saves").resolve("My World");
      Files.createDirectories(world.resolve("region"));
      Files.writeString(world.resolve("level.dat"), "level");
      Files.writeString(world.resolve("session.lock"), "lock");
      Files.write(world.resolve("region").resolve("r.0.0.mca"), new byte[200_000]);
      return world;
   }

   @Test
   void backupAndRestoreRoundTripWithoutTheLockFile() throws IOException {
      Path world = this.world();
      Path zip = this.temp.resolve("backups").resolve("My World").resolve("b.zip");
      WorldZip.Progress progress = new WorldZip.Progress();
      long size = WorldZip.zip(world, zip, "My World", progress);
      assertTrue(size > 0 && Files.size(zip) == size);
      assertEquals(1.0F, progress.fraction(), 1.0E-6F);
      assertFalse(Files.exists(zip.resolveSibling("b.zip.part")), "the temporary file is gone");

      Path restored = this.temp.resolve("saves").resolve("My World (backup)");
      WorldZip.unzip(zip, restored, new WorldZip.Progress());
      assertEquals("level", Files.readString(restored.resolve("level.dat")));
      assertEquals(200_000L, Files.size(restored.resolve("region").resolve("r.0.0.mca")));
      assertFalse(Files.exists(restored.resolve("session.lock")));
      assertEquals("level", Files.readString(world.resolve("level.dat")), "the original is untouched");
   }

   @Test
   void restoreNeverWritesIntoAnExistingFolder() throws IOException {
      Path world = this.world();
      Path zip = this.temp.resolve("b.zip");
      WorldZip.zip(world, zip, "My World", new WorldZip.Progress());
      assertThrows(IOException.class, () -> WorldZip.unzip(zip, world, new WorldZip.Progress()));
      assertEquals("lock", Files.readString(world.resolve("session.lock")));
   }

   private Path craft(String... names) throws IOException {
      Path zip = this.temp.resolve("crafted-" + names.length + "-" + Math.abs(String.join("|", names).hashCode()) + ".zip");
      try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
         for (String name : names) {
            out.putNextEntry(new ZipEntry(name));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
         }
      }

      return zip;
   }

   @Test
   void zipSlipArchivesAreRefusedBeforeAnythingIsWritten() throws IOException {
      List<String[]> evil = List.of(
         new String[]{"w/level.dat", "w/../../evil.txt"},
         new String[]{"level.dat", "../evil.txt"},
         new String[]{"level.dat", "/tmp/evil.txt"},
         new String[]{"level.dat", "C:/evil.txt"},
         new String[]{"level.dat", "a\\..\\..\\evil.txt"},
         new String[]{"../level.dat", "../evil.txt"}
      );
      for (String[] names : evil) {
         Path dest = this.temp.resolve("out").resolve("restored");
         Path zip = this.craft(names);
         assertThrows(WorldZip.UnsafeArchiveException.class, () -> WorldZip.unzip(zip, dest, new WorldZip.Progress()), String.join(", ", names));
         assertFalse(Files.exists(dest), "nothing was written for " + String.join(", ", names));
         assertFalse(Files.exists(this.temp.resolve("evil.txt")));
         assertFalse(Files.exists(this.temp.resolve("out").resolve("evil.txt")));
      }
   }

   @Test
   void aZipWithoutLevelDatIsNotAWorld() throws IOException {
      Path zip = this.craft("photos/cat.png");
      Path dest = this.temp.resolve("restored");
      assertThrows(WorldZip.UnsafeArchiveException.class, () -> WorldZip.unzip(zip, dest, new WorldZip.Progress()));
      assertFalse(Files.exists(dest));
   }

   @Test
   void aBackupWithFilesAtTheTopRestoresToo() throws IOException {
      Path zip = this.craft("level.dat", "region/r.0.0.mca");
      Path dest = this.temp.resolve("restored");
      WorldZip.unzip(zip, dest, new WorldZip.Progress());
      assertTrue(Files.exists(dest.resolve("level.dat")));
      assertTrue(Files.exists(dest.resolve("region").resolve("r.0.0.mca")));
   }

   @Test
   void safeTargetStaysInsideTheRoot() {
      Path root = this.temp.resolve("root").toAbsolutePath().normalize();
      assertNotNull(WorldZip.safeTarget(root, "region/r.0.0.mca"));
      assertNotNull(WorldZip.safeTarget(root, "a/./b"));
      assertNull(WorldZip.safeTarget(root, "a/../../b"));
      assertNull(WorldZip.safeTarget(root, ".."));
      assertNull(WorldZip.safeTarget(root, "."));
      assertNull(WorldZip.safeTarget(root, ""));
      assertNull(WorldZip.safeTarget(root, "/etc/passwd"));
      assertNull(WorldZip.safeTarget(root, "\\\\server\\share"));
      assertNull(WorldZip.safeTarget(root, "D:evil"));
      assertNull(WorldZip.safeTarget(root, "a\u0000b"));
   }

   @Test
   void commonRootIsTheSharedWorldFolder() {
      assertEquals("w", WorldZip.commonRoot(List.of("w/", "w/level.dat", "w/region/r.mca")));
      assertEquals("", WorldZip.commonRoot(List.of("w/level.dat", "v/level.dat")));
      assertEquals("", WorldZip.commonRoot(List.of("level.dat", "w/x")));
      assertEquals("", WorldZip.commonRoot(List.of("../level.dat")));
      assertEquals("", WorldZip.commonRoot(List.of()));
   }
}
