package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigMigrationsTest {
   private static JsonObject module(boolean enabled, int keybind, String... settings) {
      JsonObject module = new JsonObject();
      module.addProperty("enabled", enabled);
      module.addProperty("keybind", keybind);
      JsonObject values = new JsonObject();
      for (int i = 0; i < settings.length; i += 2) {
         values.add(settings[i], JsonFiles.GSON.fromJson(settings[i + 1], com.google.gson.JsonElement.class));
      }

      module.add("settings", values);
      return module;
   }

   private static JsonObject config(int version, Object... modules) {
      JsonObject root = new JsonObject();
      root.addProperty("version", version);
      JsonObject all = new JsonObject();
      for (int i = 0; i < modules.length; i += 2) {
         all.add((String)modules[i], (JsonObject)modules[i + 1]);
      }

      root.add("modules", all);
      return root;
   }

   private static JsonObject settings(JsonObject root, String module) {
      return root.getAsJsonObject("modules").getAsJsonObject(module).getAsJsonObject("settings");
   }

   @Test
   void currentConfigsAreLeftAlone() {
      JsonObject root = config(ConfigMigrations.CURRENT_VERSION, "ReverseStep", module(true, 30));
      String before = root.toString();
      assertTrue(ConfigMigrations.migrate(root).isEmpty());
      assertEquals(before, root.toString());
   }

   @Test
   void newerConfigsAreLeftAlone() {
      JsonObject root = config(ConfigMigrations.CURRENT_VERSION + 3, "Fast Fall", module(true, -1));
      String before = root.toString();
      assertTrue(ConfigMigrations.migrate(root).isEmpty());
      assertEquals(before, root.toString(), "a config from a newer Gemini must not be downgraded or restamped");
   }

   @Test
   void reverseStepAloneBecomesFastFallInLedgesMode() {
      JsonObject root = config(7, "ReverseStep", module(true, 30, "Height", "2.5", "Speed", "1.8"), "Fast Fall", module(false, -1, "Fall Speed", "2.0"));
      List<String> notes = ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertFalse(modules.has("ReverseStep"));
      JsonObject fastFall = modules.getAsJsonObject("Fast Fall");
      assertTrue(fastFall.get("enabled").getAsBoolean());
      assertEquals(30, fastFall.get("keybind").getAsInt(), "the key bind moves over when Fast Fall had none");
      assertEquals("Ledges", settings(root, "Fast Fall").get("When").getAsString());
      assertEquals(2.5, settings(root, "Fast Fall").get("Ledge Height").getAsDouble());
      assertEquals(1.8, settings(root, "Fast Fall").get("Ledge Speed").getAsDouble());
      assertEquals(2.0, settings(root, "Fast Fall").get("Fall Speed").getAsDouble(), "Fast Fall's own settings are untouched");
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
      assertEquals(1, notes.size());
   }

   @Test
   void bothModulesOnBecomeBothMode() {
      JsonObject root = config(7, "ReverseStep", module(true, 30), "Fast Fall", module(true, 40));
      ConfigMigrations.migrate(root);
      assertEquals("Both", settings(root, "Fast Fall").get("When").getAsString());
      assertEquals(40, root.getAsJsonObject("modules").getAsJsonObject("Fast Fall").get("keybind").getAsInt(), "an existing key bind wins");
   }

   @Test
   void reverseStepOffLeavesFastFallAsItWas() {
      JsonObject root = config(7, "ReverseStep", module(false, -1, "Height", "4.0"), "Fast Fall", module(true, -1));
      ConfigMigrations.migrate(root);
      assertFalse(settings(root, "Fast Fall").has("When"), "the default mode (Falling) keeps Fast Fall behaving as before");
      assertTrue(root.getAsJsonObject("modules").getAsJsonObject("Fast Fall").get("enabled").getAsBoolean());
      assertEquals(4.0, settings(root, "Fast Fall").get("Ledge Height").getAsDouble(), "its settings are still carried for later use");
   }

   @Test
   void reverseStepIsFoundUnderAnySpellingAndWithoutFastFallPresent() {
      JsonObject root = config(7, "reverse_step", module(true, -1));
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertEquals(1, modules.size());
      assertEquals("Ledges", settings(root, "Fast Fall").get("When").getAsString());
   }

   @Test
   void olderConfigsRunEveryStepInOrder() {
      // Version 4: AutoClutch still exists (merged in 7), key binds are GLFW codes (converted in 6), Disabler exists (removed in 5).
      JsonObject root = config(4, "AutoClutch", module(true, 71), "Disabler", module(true, -1), "ReverseStep", module(true, 82));
      List<String> notes = ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertFalse(modules.has("Disabler"));
      assertFalse(modules.has("AutoClutch"));
      assertFalse(modules.has("ReverseStep"));
      assertTrue(modules.getAsJsonObject("Clutch").get("enabled").getAsBoolean());
      assertTrue(modules.getAsJsonObject("Fast Fall").get("enabled").getAsBoolean());
      assertEquals(gemini.input.Keys.fromGlfw(71), modules.getAsJsonObject("Clutch").get("keybind").getAsInt(), "the key was converted before it was moved");
      assertEquals(gemini.input.Keys.fromGlfw(82), modules.getAsJsonObject("Fast Fall").get("keybind").getAsInt());
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
      assertTrue(notes.size() >= 4, notes.toString());
   }

   @Test
   void migratingTwiceChangesNothingTheSecondTime() {
      JsonObject root = config(6, "ReverseStep", module(true, 30), "AutoClutch", module(true, -1));
      ConfigMigrations.migrate(root);
      String once = root.toString();
      assertTrue(ConfigMigrations.migrate(root).isEmpty());
      assertEquals(once, root.toString());
   }

   @Test
   void damagedEntriesDoNotStopTheUpgrade() {
      JsonObject root = config(7, "Fast Fall", module(false, -1));
      root.getAsJsonObject("modules").addProperty("ReverseStep", "not an object");
      root.getAsJsonObject("modules").getAsJsonObject("Fast Fall").addProperty("settings", 5);
      ConfigMigrations.migrate(root);
      assertFalse(root.getAsJsonObject("modules").has("ReverseStep"));
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
   }

   @Test
   void theHudModuleListBecomesTheArrayListWidgetInTheSameCorner() {
      JsonObject root = config(
         8,
         "HUD",
         module(
            true, -1, "Watermark", "true", "ArrayList", "true", "List Side", "\"Left\"", "List Anchor", "\"Bottom\"", "Sort", "\"Length\"", "Colors",
            "\"Pulsing Rainbow\"", "Side Bar", "false", "Glow Outline", "false", "Module Info", "false", "Opacity", "0.4", "Text Shadow", "false"
         )
      );
      List<String> notes = ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      JsonObject widget = modules.getAsJsonObject("ArrayList");
      assertTrue(widget.get("enabled").getAsBoolean(), "a list that was on stays on");
      JsonObject settings = settings(root, "ArrayList");
      assertEquals("Bottom Left", settings.get("Corner").getAsString());
      assertEquals("Width", settings.get("Sort").getAsString());
      assertEquals("Rainbow", settings.get("Colors").getAsString());
      assertEquals("None", settings.get("Side Bar").getAsString());
      assertFalse(settings.get("Outline").getAsBoolean());
      assertFalse(settings.get("Show Info").getAsBoolean());
      assertEquals(0.4, settings.get("Opacity").getAsDouble());
      assertFalse(settings.get("Text Shadow").getAsBoolean());
      JsonObject hud = settings(root, "HUD");
      for (String old : ConfigMigrations.OLD_LIST_SETTINGS) {
         assertFalse(hud.has(old), "the HUD module no longer carries " + old);
      }

      assertTrue(hud.get("Watermark").getAsBoolean(), "unrelated HUD settings stay");
      assertTrue(hud.has("Text Shadow"), "the HUD keeps its own text shadow switch");
      assertEquals(1, notes.size());
   }

   @Test
   void aListThatWasOffStaysOffAndDefaultsToTheTopRight() {
      JsonObject root = config(8, "HUD", module(true, -1, "ArrayList", "false", "Colors", "\"Neon Wave\""));
      ConfigMigrations.migrate(root);
      JsonObject widget = root.getAsJsonObject("modules").getAsJsonObject("ArrayList");
      assertFalse(widget.get("enabled").getAsBoolean());
      assertEquals("Top Right", settings(root, "ArrayList").get("Corner").getAsString());
      assertEquals("Wave", settings(root, "ArrayList").get("Colors").getAsString());
   }

   @Test
   void anExistingArrayListWidgetIsNotDuplicatedOrOverwritten() {
      JsonObject root = config(8, "HUD", module(true, -1, "ArrayList", "true", "List Side", "\"Left\""), "ArrayList", module(false, 12, "Corner", "\"Top Right\""));
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertEquals(2, modules.size());
      assertFalse(modules.getAsJsonObject("ArrayList").get("enabled").getAsBoolean());
      assertEquals("Top Right", settings(root, "ArrayList").get("Corner").getAsString());
      assertFalse(settings(root, "HUD").has("ArrayList"));
   }

   @Test
   void configsWithoutTheOldListAreUntouchedByTheListStep() {
      JsonObject root = config(8, "HUD", module(true, -1, "Watermark", "false"));
      ConfigMigrations.migrate(root);
      assertFalse(root.getAsJsonObject("modules").has("ArrayList"), "the widget's own default applies");
   }

   @Test
   void aConfigWithoutVersionIsTreatedAsTheOldest() {
      JsonObject root = new JsonObject();
      root.add("modules", new JsonObject());
      assertEquals(0, ConfigMigrations.versionOf(root));
      ConfigMigrations.migrate(root);
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
   }

   @Test
   void projectileAimbotBecomesBowAimbot() {
      JsonObject root = config(9, "ProjectileAimbot", module(true, 66, "Range", "30.0"));
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertFalse(modules.has("ProjectileAimbot"));
      JsonObject moved = modules.getAsJsonObject("Bow Aimbot");
      assertTrue(moved.get("enabled").getAsBoolean());
      assertEquals(66, moved.get("keybind").getAsInt());
      assertEquals(30.0, settings(root, "Bow Aimbot").get("Range").getAsDouble());
   }

   @Test
   void existingBowAimbotIsNotOverwritten() {
      JsonObject root = config(9, "ProjectileAimbot", module(true, 66), "Bow Aimbot", module(false, 12));
      ConfigMigrations.migrate(root);
      assertEquals(12, root.getAsJsonObject("modules").getAsJsonObject("Bow Aimbot").get("keybind").getAsInt());
   }

   @Test
   void killAuraPriorityTakesTheSharedModes() {
      String[][] cases = {{"Distance", "Closest"}, {"Health", "Lowest Health"}, {"Angle", "Closest To Crosshair"}, {"Armor", "Least Armor"}};
      for (String[] c : cases) {
         JsonObject root = config(9, "KillAura", module(true, -1, "Priority", "\"" + c[0] + "\""));
         ConfigMigrations.migrate(root);
         assertEquals(c[1], settings(root, "KillAura").get("Priority").getAsString());
      }
   }

   @Test
   void killAuraHighlightBecomesTargetBox() {
      JsonObject off = config(9, "KillAura", module(true, -1, "Highlight", "false"));
      ConfigMigrations.migrate(off);
      assertEquals("Off", settings(off, "KillAura").get("Target Box").getAsString());
      assertFalse(settings(off, "KillAura").has("Highlight"));
      JsonObject on = config(9, "KillAura", module(true, -1, "Highlight", "true"));
      ConfigMigrations.migrate(on);
      assertFalse(settings(on, "KillAura").has("Target Box"), "on keeps the new default box");
      assertFalse(settings(on, "KillAura").has("Highlight"));
   }

   @Test
   void styleValuesGetPlainNames() {
      JsonObject root = config(10,
         "HUD", module(true, -1, "Watermark Style", "\"LiquidBounce Pill\""),
         "ArrayList", module(true, -1, "Colors", "\"Astolfo\""),
         "ClickGUI", module(true, -1, "Theme", "\"Neon\""));
      ConfigMigrations.migrate(root);
      assertEquals("Compact", settings(root, "HUD").get("Watermark Style").getAsString());
      assertEquals("Pink-Blue Wave", settings(root, "ArrayList").get("Colors").getAsString());
      assertEquals("Purple", settings(root, "ClickGUI").get("Theme").getAsString());
   }

   @Test
   void oldHudListSettingsStillReachThePlainNames() {
      // A 3.2.0 config: v9 moves the list to the ArrayList widget, then v11 renames the colour mode.
      JsonObject root = config(8, "HUD", module(true, -1, "Watermark Style", "\"Nextgen Card\""));
      ConfigMigrations.migrate(root);
      assertEquals("Card", settings(root, "HUD").get("Watermark Style").getAsString());
   }
}
