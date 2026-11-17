package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstalledModsTest {
   @TempDir
   Path mods;

   private Path jar(String name, String modJson) throws IOException {
      Path path = this.mods.resolve(name);
      try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out)) {
         if (modJson != null) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(modJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
         }

         zip.putNextEntry(new ZipEntry("a.class"));
         zip.closeEntry();
      }

      return path;
   }

   @Test
   void statesFollowWhatFabricLoadsNextStart() throws IOException {
      Path running = this.jar("sodium.jar", "{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"0.9.3\"}");
      this.jar("modmenu-21.0.1.jar", "{\"id\":\"modmenu\",\"name\":\"Mod Menu\",\"version\":\"21.0.1\"}");
      Path wasRunning = this.jar("lithium.jar", "{\"id\":\"lithium\",\"name\":\"Lithium\",\"version\":\"1\"}");
      Files.move(wasRunning, this.mods.resolve("lithium.jar.disabled"));
      this.jar("old.jar.disabled", "{\"id\":\"old\",\"name\":\"Old\",\"version\":\"1\"}");
      this.jar(".hidden.jar", "{\"id\":\"hidden\"}");
      Files.writeString(this.mods.resolve("notes.txt"), "not a mod");
      Files.createDirectories(this.mods.resolve(".gemini-trash"));
      this.jar("nometa.jar", null);
      List<InstalledMods.Loaded> loaded = List.of(
         new InstalledMods.Loaded("sodium", "Sodium", "0.9.3", List.of(running)),
         new InstalledMods.Loaded("lithium", "Lithium", "1", List.of(wasRunning))
      );
      List<InstalledMods.Entry> entries = InstalledMods.scan(this.mods, loaded, Set.of("fabric-api", "gemini"), null);
      assertEquals(List.of("Lithium", "Mod Menu", "nometa.jar", "Old", "Sodium"), entries.stream().map(InstalledMods.Entry::name).toList());
      assertEquals(InstalledMods.State.OFF_NEXT_START, entries.get(0).state());
      assertEquals(InstalledMods.State.NEXT_START, entries.get(1).state());
      assertEquals("21.0.1", entries.get(1).version());
      assertEquals(InstalledMods.State.NEXT_START, entries.get(2).state());
      assertEquals(InstalledMods.State.DISABLED, entries.get(3).state());
      assertTrue(entries.get(3).disabled());
      assertEquals(InstalledMods.State.LOADED, entries.get(4).state());
   }

   @Test
   void essentialModsAreFlagged() throws IOException {
      Path api = this.jar("fabric-api-0.166.2.jar", "{\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.166.2\"}");
      Path own = this.jar("gemini-3.3.0.jar", "{\"id\":\"something\"}");
      this.jar("other.jar", "{\"id\":\"other\"}");
      List<InstalledMods.Entry> entries = InstalledMods.scan(this.mods, List.of(), Set.of("fabric-api", "gemini"), own);
      for (InstalledMods.Entry entry : entries) {
         boolean expected = entry.path().equals(api) || entry.path().equals(own);
         assertEquals(expected, entry.essential(), entry.fileName());
      }
   }

   @Test
   void missingFolderIsEmpty() throws IOException {
      assertTrue(InstalledMods.scan(this.mods.resolve("nope"), List.of(), Set.of(), null).isEmpty());
      assertFalse(InstalledMods.readModInfo(this.mods.resolve("nope.jar")).id().length() > 0);
   }
}
