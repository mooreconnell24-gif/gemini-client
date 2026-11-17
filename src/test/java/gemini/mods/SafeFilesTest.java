package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SafeFilesTest {
   @Test
   void onlyModrinthCdnOverHttps() {
      assertTrue(SafeFiles.allowedDownload("https://cdn.modrinth.com/data/mOgUt4GM/versions/xckkBHD5/modmenu-21.0.1.jar"));
      assertTrue(SafeFiles.allowedDownload("https://CDN.MODRINTH.COM/data/x.jar"));
      assertTrue(SafeFiles.allowedDownload("https://cdn.modrinth.com:443/data/x.jar"));
      assertFalse(SafeFiles.allowedDownload("http://cdn.modrinth.com/data/x.jar"));
      assertFalse(SafeFiles.allowedDownload("https://cdn.modrinth.com.evil.example/data/x.jar"));
      assertFalse(SafeFiles.allowedDownload("https://evil.example/cdn.modrinth.com/x.jar"));
      assertFalse(SafeFiles.allowedDownload("https://user@cdn.modrinth.com/x.jar"));
      assertFalse(SafeFiles.allowedDownload("https://cdn.modrinth.com:8443/x.jar"));
      assertFalse(SafeFiles.allowedDownload("https://github.com/x/releases/x.jar"));
      assertFalse(SafeFiles.allowedDownload("file:///etc/passwd"));
      assertFalse(SafeFiles.allowedDownload(""));
      assertFalse(SafeFiles.allowedDownload(null));
      assertFalse(SafeFiles.allowedDownload("https://cdn.modrinth.com\\@evil.example/x.jar"));
   }

   @Test
   void namesCannotLeaveTheFolder() {
      assertEquals("evil.jar", SafeFiles.sanitizeFilename("../../evil.jar", ".jar"));
      assertEquals("evil.jar", SafeFiles.sanitizeFilename("..\\..\\evil.jar", ".jar"));
      assertEquals("evil.jar", SafeFiles.sanitizeFilename("/etc/evil.jar", ".jar"));
      assertEquals("hidden.jar", SafeFiles.sanitizeFilename(".hidden.jar", ".jar"));
      assertEquals("opt.jar", SafeFiles.sanitizeFilename("-opt.jar", ".jar"));
      assertEquals("a.b.jar", SafeFiles.sanitizeFilename("a..b.jar", ".jar"));
      assertNull(SafeFiles.sanitizeFilename("..", ".jar"));
      assertNull(SafeFiles.sanitizeFilename(".jar", ".jar"));
      assertNull(SafeFiles.sanitizeFilename("", ".jar"));
   }

   @Test
   void keepsNormalNamesAndCleansOddCharacters() {
      assertEquals("modmenu-21.0.1.jar", SafeFiles.sanitizeFilename("modmenu-21.0.1.jar", ".jar"));
      assertEquals("iris-fabric-1.11.7+mc26.3.jar", SafeFiles.sanitizeFilename("iris-fabric-1.11.7+mc26.3.jar", ".jar"));
      // the real file name of a resource pack on Modrinth, with a formatting code in it
      String pack = Modrinth.parseVersions(Fixtures.modrinth("versions_low_on_fire.json")).get(0).primaryFile().filename();
      assertEquals("LowOnFire v26.2§8.zip", pack);
      assertEquals("LowOnFire_v26.2_8.zip", SafeFiles.sanitizeFilename(pack, ".zip"));
      assertEquals("a_b.jar", SafeFiles.sanitizeFilename("a\u0000\nb.jar", ".jar"));
      assertEquals("Mod.jar", SafeFiles.sanitizeFilename("Mod.JAR", ".jar"));
   }

   @Test
   void onlyTheAllowedExtension() {
      assertNull(SafeFiles.sanitizeFilename("mod.zip", ".jar"));
      assertNull(SafeFiles.sanitizeFilename("pack.jar", ".zip"));
      assertNull(SafeFiles.sanitizeFilename("mod.jar.exe", ".jar"));
      assertNull(SafeFiles.sanitizeFilename("mod.jar/", ".jar"));
   }

   @Test
   void longNamesKeepTheirExtension() {
      String name = SafeFiles.sanitizeFilename("a".repeat(300) + ".jar", ".jar");
      assertTrue(name.endsWith(".jar"));
      assertTrue(name.length() <= 120);
   }

   @Test
   void hashChecks() {
      byte[] data = "gemini".getBytes(StandardCharsets.UTF_8);
      String good = SafeFiles.sha512(data);
      assertEquals(128, good.length());
      assertTrue(SafeFiles.sameHash(good, SafeFiles.sha512(data)));
      assertTrue(SafeFiles.sameHash(good.toUpperCase(), good));
      assertFalse(SafeFiles.sameHash(good, SafeFiles.sha512("gemini!".getBytes(StandardCharsets.UTF_8))));
      assertFalse(SafeFiles.sameHash("", good));
      assertFalse(SafeFiles.sameHash(null, good));
      assertFalse(SafeFiles.sameHash(good.substring(1), good));
   }

   @Test
   void directChildOnly() {
      Path mods = Path.of("/game/mods");
      assertTrue(SafeFiles.directChild(mods, mods.resolve("a.jar")));
      assertFalse(SafeFiles.directChild(mods, mods.resolve("sub/a.jar")));
      assertFalse(SafeFiles.directChild(mods, mods.resolve("../a.jar")));
   }
}
