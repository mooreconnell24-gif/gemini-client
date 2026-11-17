package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateCheckTest {
   @Test
   void findsTheNewerModMenuAndNothingForCurrentFabricApi() {
      // recorded with the hashes of Mod Menu 21.0.0 and Fabric API 0.166.2 (the newest for 26.3)
      Map<String, Modrinth.Version> current = Modrinth.parseVersionsByHash(Fixtures.modrinth("version_files.json"));
      Map<String, Modrinth.Version> latest = Modrinth.parseVersionsByHash(Fixtures.modrinth("version_files_update.json"));
      Map<String, UpdateCheck.Update> updates = UpdateCheck.find(current, latest);
      assertEquals(1, updates.size());
      UpdateCheck.Update update = updates.values().iterator().next();
      assertEquals("21.0.0", update.current().versionNumber());
      assertEquals("21.0.1", update.latest().versionNumber());
      assertTrue(update.sha512().startsWith("588c0c557963f477"));
   }

   @Test
   void unknownCurrentVersionStillComparesByFileHash() {
      Map<String, Modrinth.Version> latest = Modrinth.parseVersionsByHash(Fixtures.modrinth("version_files_update.json"));
      Map<String, UpdateCheck.Update> updates = UpdateCheck.find(Map.of(), latest);
      assertEquals(1, updates.size());
      assertEquals(null, updates.values().iterator().next().current());
   }

   @Test
   void sameVersionIdIsNotAnUpdate() {
      Modrinth.Version v = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json")).get(0);
      Map<String, UpdateCheck.Update> updates = UpdateCheck.find(Map.of("abc", v), Map.of("abc", v));
      assertTrue(updates.isEmpty());
      List<Modrinth.Version> none = List.of();
      assertTrue(UpdateCheck.find(Map.of(), Map.of()).isEmpty(), none.toString());
   }
}
