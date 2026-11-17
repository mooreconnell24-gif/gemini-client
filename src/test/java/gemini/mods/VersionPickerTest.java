package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VersionPickerTest {
   private static final List<Modrinth.Version> CLOTH = Modrinth.parseVersions(Fixtures.modrinth("versions_cloth_all.json"));

   @Test
   void picksNewestFabricBuildForTheGameVersion() {
      // the list also holds NeoForge builds and builds for 26.2 / 26.1 / 1.21.11
      assertEquals("fg2uyxOW", VersionPicker.best(CLOTH, "fabric", "26.3").orElseThrow().id());
      assertEquals("Nv3xnWXd", VersionPicker.best(CLOTH, "fabric", "26.2").orElseThrow().id());
      assertEquals("GchRXPnb", VersionPicker.best(CLOTH, "neoforge", "26.3").orElseThrow().id());
   }

   @Test
   void nothingForOtherLoadersOrVersions() {
      assertTrue(VersionPicker.best(CLOTH, "quilt", "26.3").isEmpty());
      assertTrue(VersionPicker.best(CLOTH, "fabric", "26.4").isEmpty());
      // "26.3" must match exactly, not as a prefix of "26.3.1" or a snapshot name
      assertTrue(VersionPicker.best(CLOTH, "fabric", "26").isEmpty());
   }

   @Test
   void releaseBeatsNewerBeta() {
      List<Modrinth.Version> modmenu = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json"));
      assertEquals("21.0.1", VersionPicker.best(modmenu, "fabric", "26.3").orElseThrow().versionNumber());
      String beta = "{\"id\":\"b\",\"version_type\":\"beta\",\"date_published\":\"2027-01-01T00:00:00Z\",\"loaders\":[\"fabric\"],"
         + "\"game_versions\":[\"26.3\"],\"files\":[{\"url\":\"https://cdn.modrinth.com/x.jar\",\"filename\":\"x.jar\",\"primary\":true}]}";
      List<Modrinth.Version> withBeta = new java.util.ArrayList<>(modmenu);
      withBeta.add(Modrinth.parseVersion(beta));
      assertEquals("21.0.1", VersionPicker.best(withBeta, "fabric", "26.3").orElseThrow().versionNumber());
      assertEquals("b", VersionPicker.best(List.of(Modrinth.parseVersion(beta)), "fabric", "26.3").orElseThrow().id());
   }

   @Test
   void quiltAndFabricListedTogetherCountsAsFabric() {
      Modrinth.Version modmenu = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json")).get(0);
      assertTrue(VersionPicker.compatible(modmenu, "fabric", "26.3"));
      assertTrue(VersionPicker.compatible(modmenu, "FABRIC", "26.3"));
   }

   @Test
   void resourcePacksUseTheMinecraftLoader() {
      List<Modrinth.Version> pack = Modrinth.parseVersions(Fixtures.modrinth("versions_low_on_fire.json"));
      assertTrue(VersionPicker.best(pack, "minecraft", "26.3").isPresent());
      assertTrue(VersionPicker.best(pack, "fabric", "26.3").isEmpty());
   }

   @Test
   void versionWithoutFilesIsNeverPicked() {
      Modrinth.Version empty = Modrinth.parseVersion("{\"id\":\"e\",\"loaders\":[\"fabric\"],\"game_versions\":[\"26.3\"],\"files\":[]}");
      assertFalse(VersionPicker.compatible(empty, "fabric", "26.3"));
   }
}
