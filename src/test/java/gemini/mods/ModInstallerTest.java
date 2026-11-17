package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModInstallerTest {
   private static final byte[] JAR = "PK\u0003\u0004 pretend jar".getBytes(StandardCharsets.ISO_8859_1);
   private static final String URL = "https://cdn.modrinth.com/data/abc/versions/def/lib-1.0.jar";
   @TempDir
   Path game;

   /** A downloader that "receives" {@code body}, the way the real one writes a new file. */
   private static ModInstaller.Downloader serving(byte[] body, List<String> requested) {
      return (uri, target, max, progress) -> {
         requested.add(uri.toString());
         if (body.length > max) {
            throw new IOException("too large");
         }

         Files.write(target, body, java.nio.file.StandardOpenOption.CREATE_NEW);
         progress.accept(body.length);
      };
   }

   private ModInstaller installer(byte[] body, List<String> requested) {
      return new ModInstaller(this.game, serving(body, requested), path -> path.getFileName().toString().startsWith("gemini"));
   }

   private static Modrinth.File file(String url, String name, String sha512, long size) {
      return new Modrinth.File(url, name, sha512, "", size, true);
   }

   private List<String> modsFolder() throws IOException {
      try (Stream<Path> files = Files.list(this.game.resolve("mods"))) {
         return files.map(p -> p.getFileName().toString()).sorted().toList();
      }
   }

   @Test
   void goodHashIsInstalled() throws IOException {
      List<String> requested = new ArrayList<>();
      long[] progress = {0};
      ModInstaller.Placed placed = this.installer(JAR, requested).install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), b -> progress[0] = b);
      assertFalse(placed.alreadyThere());
      assertEquals(this.game.resolve("mods/lib-1.0.jar"), placed.path());
      assertArrayEquals(JAR, Files.readAllBytes(placed.path()));
      assertEquals(SafeFiles.sha512(JAR), SafeFiles.sha512(placed.path()));
      assertEquals(List.of("lib-1.0.jar"), this.modsFolder(), "no temporary file left behind");
      assertEquals(JAR.length, progress[0]);
      assertEquals(List.of(URL), requested);
   }

   @Test
   void badHashIsRefusedAndLeavesNothing() throws IOException {
      byte[] tampered = "PK something else".getBytes(StandardCharsets.ISO_8859_1);
      ModInstaller installer = this.installer(tampered, new ArrayList<>());
      ModInstaller.Refused refused = assertThrows(ModInstaller.Refused.class,
         () -> installer.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), 0), null));
      assertTrue(refused.getMessage().contains("SHA-512"));
      assertEquals(List.of(), this.modsFolder());
   }

   @Test
   void wrongSizeIsRefused() throws IOException {
      ModInstaller installer = this.installer(JAR, new ArrayList<>());
      assertThrows(IOException.class, () -> installer.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length - 3), null));
      assertEquals(List.of(), this.modsFolder());
   }

   @Test
   void otherHostsAreNeverContacted() {
      List<String> requested = new ArrayList<>();
      ModInstaller installer = this.installer(JAR, requested);
      assertThrows(ModInstaller.Refused.class,
         () -> installer.install(ProjectType.MOD, file("https://evil.example/lib.jar", "lib.jar", SafeFiles.sha512(JAR), JAR.length), null));
      assertThrows(ModInstaller.Refused.class,
         () -> installer.install(ProjectType.MOD, file("http://cdn.modrinth.com/lib.jar", "lib.jar", SafeFiles.sha512(JAR), JAR.length), null));
      assertTrue(requested.isEmpty());
   }

   @Test
   void onlyJarsForModsAndZipsForPacks() throws IOException {
      List<String> requested = new ArrayList<>();
      ModInstaller installer = this.installer(JAR, requested);
      assertThrows(ModInstaller.Refused.class, () -> installer.install(ProjectType.MOD, file(URL, "lib.zip", SafeFiles.sha512(JAR), JAR.length), null));
      assertThrows(ModInstaller.Refused.class, () -> installer.install(ProjectType.MOD, file(URL, "lib.jar.exe", SafeFiles.sha512(JAR), JAR.length), null));
      assertThrows(ModInstaller.Refused.class, () -> installer.install(ProjectType.RESOURCE_PACK, file(URL, "pack.jar", SafeFiles.sha512(JAR), JAR.length), null));
      assertTrue(requested.isEmpty());
      Path pack = installer.install(ProjectType.RESOURCE_PACK, file(URL, "../pack.zip", SafeFiles.sha512(JAR), JAR.length), null).path();
      assertEquals(this.game.resolve("resourcepacks/pack.zip"), pack);
   }

   @Test
   void neverOverwritesAFileItDidNotPutThere() throws IOException {
      Files.createDirectories(this.game.resolve("mods"));
      byte[] mine = "the user's own jar".getBytes(StandardCharsets.UTF_8);
      Files.write(this.game.resolve("mods/lib-1.0.jar"), mine);
      List<String> requested = new ArrayList<>();
      ModInstaller installer = this.installer(JAR, requested);
      assertThrows(ModInstaller.Refused.class, () -> installer.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), null));
      assertArrayEquals(mine, Files.readAllBytes(this.game.resolve("mods/lib-1.0.jar")));
      assertTrue(requested.isEmpty(), "refused before downloading");
   }

   @Test
   void sameFileAlreadyThereIsNotDownloadedAgain() throws IOException {
      Files.createDirectories(this.game.resolve("mods"));
      Files.write(this.game.resolve("mods/lib-1.0.jar"), JAR);
      List<String> requested = new ArrayList<>();
      ModInstaller.Placed placed = this.installer(JAR, requested).install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), null);
      assertTrue(placed.alreadyThere());
      assertTrue(requested.isEmpty());
   }

   @Test
   void disableEnableAndTrash() throws IOException {
      ModInstaller installer = this.installer(JAR, new ArrayList<>());
      Path jar = installer.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), null).path();
      Path disabled = installer.disable(jar);
      assertEquals("lib-1.0.jar.disabled", disabled.getFileName().toString());
      assertEquals(List.of("lib-1.0.jar.disabled"), this.modsFolder());
      Path enabled = installer.enable(disabled);
      assertEquals(jar, enabled);
      Path trashed = installer.trash(enabled);
      assertEquals(this.game.resolve("mods/.gemini-trash/lib-1.0.jar"), trashed);
      assertArrayEquals(JAR, Files.readAllBytes(trashed), "moved, not deleted");
      // a second file with the same name goes next to the first one in the trash
      Path again = installer.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), null).path();
      assertEquals(this.game.resolve("mods/.gemini-trash/1-lib-1.0.jar"), installer.trash(again));
   }

   @Test
   void updateMovesTheOldVersionToTheTrashAfterVerifying() throws IOException {
      Files.createDirectories(this.game.resolve("mods"));
      Path old = this.game.resolve("mods/lib-0.9.jar");
      Files.write(old, "old".getBytes(StandardCharsets.UTF_8));
      ModInstaller good = this.installer(JAR, new ArrayList<>());
      // a failed check keeps the old version where it is
      ModInstaller bad = this.installer("tampered".getBytes(StandardCharsets.UTF_8), new ArrayList<>());
      assertThrows(ModInstaller.Refused.class, () -> bad.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), 0), old, null));
      assertTrue(Files.exists(old));
      good.install(ProjectType.MOD, file(URL, "lib-1.0.jar", SafeFiles.sha512(JAR), JAR.length), old, null);
      assertEquals(List.of(".gemini-trash", "lib-1.0.jar"), this.modsFolder());
   }

   @Test
   void protectedFilesAreLeftAlone() throws IOException {
      Files.createDirectories(this.game.resolve("mods"));
      Path gemini = Files.write(this.game.resolve("mods/gemini-3.3.0.jar"), JAR);
      ModInstaller installer = this.installer(JAR, new ArrayList<>());
      assertThrows(ModInstaller.Refused.class, () -> installer.disable(gemini));
      assertThrows(ModInstaller.Refused.class, () -> installer.trash(gemini));
      assertTrue(Files.exists(gemini));
   }

   @Test
   void onlyFilesDirectlyInMods() throws IOException {
      Path outside = Files.write(this.game.resolve("options.txt"), JAR);
      ModInstaller installer = this.installer(JAR, new ArrayList<>());
      assertThrows(ModInstaller.Refused.class, () -> installer.trash(outside));
      assertThrows(ModInstaller.Refused.class, () -> installer.disable(this.game.resolve("mods/../options.txt")));
      assertTrue(Files.exists(outside));
   }

   @Test
   void joinsNames() {
      assertEquals("", ModInstaller.joinNames(List.of()));
      assertEquals("A", ModInstaller.joinNames(List.of("A")));
      assertEquals("A and B", ModInstaller.joinNames(List.of("A", "B")));
      assertEquals("A, B and C", ModInstaller.joinNames(List.of("A", "B", "C")));
   }
}
