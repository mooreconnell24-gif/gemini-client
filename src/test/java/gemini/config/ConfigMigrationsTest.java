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

   @Test
   void misleadingModuleNamesMoveTheirSettings() {
      JsonObject root = config(10, "Fucker", module(true, 21, "Range", "4.0"), "KeepY", module(false, -1), "Presets", module(false, -1, "Preset", "\"LiquidBounce\""));
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertFalse(modules.has("Fucker"));
      // Version 11 renamed these two; version 12 then removed Bed Breaker (Nuker's filter) and made Auto Hop a mode of Auto Jump.
      assertFalse(modules.has("Bed Breaker"));
      assertFalse(modules.has("Auto Hop"));
      assertFalse(modules.has("KeepY"));
      assertFalse(modules.getAsJsonObject("Auto Jump").get("enabled").getAsBoolean(), "KeepY was off, so Auto Jump stays off");
      assertEquals("General", settings(root, "Presets").get("Preset").getAsString());
   }

   // ---- version 12: the curated module list ----

   private static JsonObject entry(JsonObject root, String module) {
      return root.getAsJsonObject("modules").getAsJsonObject(module);
   }

   private static boolean on(JsonObject root, String module) {
      return entry(root, module).get("enabled").getAsBoolean();
   }

   private static int key(JsonObject root, String module) {
      return entry(root, module).get("keybind").getAsInt();
   }

   private static String text(JsonObject root, String module, String setting) {
      return settings(root, module).get(setting).getAsString();
   }

   @Test
   void renamedModulesKeepTheirSwitchKeyAndSettings() {
      JsonObject root = config(
         11,
         "Aim", module(true, 40, "Snappiness", "0.8"),
         "Rotation", module(false, 41, "Snap Step", "45.0"),
         "Search", module(true, -1),
         "Camera Tweaks", module(true, 42, "Distance", "12.0"),
         "World Rules", module(true, -1, "Keep Inventory", "true"),
         "Unfocused CPU", module(true, -1, "Background FPS", "10.0")
      );
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      for (String gone : List.of("Aim", "Rotation", "Search", "Camera Tweaks", "World Rules", "Unfocused CPU")) {
         assertFalse(modules.has(gone), gone);
      }

      assertTrue(on(root, "Rotations"));
      assertEquals(40, key(root, "Rotations"));
      assertEquals(0.8, settings(root, "Rotations").get("Snappiness").getAsDouble());
      assertFalse(on(root, "Rotation Lock"), "Rotation and Rotations are different modules and must not be mixed up");
      assertEquals(41, key(root, "Rotation Lock"));
      assertTrue(on(root, "Block ESP"));
      assertEquals(12.0, settings(root, "Camera Clip").get("Distance").getAsDouble());
      assertTrue(settings(root, "World Tools").get("Keep Inventory").getAsBoolean());
      assertEquals(10.0, settings(root, "Background FPS").get("Background FPS").getAsDouble());
   }

   @Test
   void spacingOnlyRenamesAreLeftToTheLoader() {
      // "KillAura" and "Kill Aura" are the same key to the config loader, so the entry is not moved.
      JsonObject root = config(11, "KillAura", module(true, 19, "Version", "\"1.8 Legacy CPS\"", "Legacy CPS", "9.0", "Lag Comp", "false"));
      ConfigMigrations.migrate(root);
      assertTrue(on(root, "KillAura"));
      assertEquals("CPS", text(root, "KillAura", "Timing"));
      assertEquals(9.0, settings(root, "KillAura").get("CPS").getAsDouble());
      assertFalse(settings(root, "KillAura").get("Predict").getAsBoolean());
      assertFalse(settings(root, "KillAura").has("Version"));
      assertFalse(settings(root, "KillAura").has("Legacy CPS"));
   }

   @Test
   void settingsAndModeValuesOfSurvivingModulesGetTheirNewNames() {
      JsonObject root = config(
         11,
         "Flight", module(true, -1, "Mode", "\"Packet\"", "Setback Damp", "true"),
         "Speed", module(true, -1, "Mode", "\"YPort\"", "Air Boost", "false", "Setback Pause", "true"),
         "NoFall", module(true, -1, "Mode", "\"MLG\""),
         "Velocity", module(true, -1, "Mode", "\"Packet\"", "Chance", "80.0"),
         "Scaffold", module(true, -1, "Sprint", "\"Telly\"", "Rotate", "\"Snap\"", "Raycast Check", "true", "Keep Y", "false"),
         "ESP", module(true, -1, "Mode", "\"Shader Glow\"", "3D Health Bar", "false"),
         "Fullbright", module(true, -1, "Mode", "\"Effect\""),
         "AntiLag", module(true, -1, "Mode", "\"Aggressive\"", "Block Smoke & Campfires", "false"),
         "AutoTool", module(true, -1, "Weapons", "true")
      );
      ConfigMigrations.migrate(root);
      assertEquals("Motion", text(root, "Flight", "Mode"));
      assertFalse(settings(root, "Flight").has("Setback Damp"));
      assertEquals("Y-Port", text(root, "Speed", "Mode"));
      assertFalse(settings(root, "Speed").get("Air Control").getAsBoolean());
      assertFalse(settings(root, "Speed").has("Setback Pause"));
      assertEquals("Water Bucket", text(root, "NoFall", "Mode"));
      assertEquals("Reduce", text(root, "Velocity", "Mode"));
      assertFalse(settings(root, "Velocity").has("Chance"));
      assertEquals("On", text(root, "Scaffold", "Sprint"));
      assertTrue(settings(root, "Scaffold").get("Keep Y").getAsBoolean(), "Telly was sprinting plus Keep Y");
      assertEquals("Visible", text(root, "Scaffold", "Rotate"));
      assertFalse(settings(root, "Scaffold").has("Raycast Check"));
      assertEquals("Outline", text(root, "ESP", "Mode"));
      assertFalse(settings(root, "ESP").get("Health Bar").getAsBoolean());
      assertEquals("Night Vision", text(root, "Fullbright", "Mode"));
      assertEquals("Strict", text(root, "AntiLag", "Mode"));
      assertFalse(settings(root, "AntiLag").get("Hide Smoke").getAsBoolean());
      assertFalse(settings(root, "AutoTool").has("Weapons"));
   }

   @Test
   void aMergedModuleSwitchesTheSurvivorOnAndGivesItsKeyOnlyWhenThereIsNone() {
      JsonObject root = config(
         11,
         "WTap", module(true, 33),
         "EdgeJump", module(true, 34),
         "Parkour", module(false, 50),
         "Hole Pull", module(false, 35)
      );
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertFalse(modules.has("WTap"));
      assertTrue(on(root, "Super Knockback"), "created and switched on");
      assertEquals(33, key(root, "Super Knockback"));
      assertTrue(on(root, "Parkour"));
      assertEquals(50, key(root, "Parkour"), "Parkour's own key wins");
      assertFalse(on(root, "Hole Snap"), "a merged module that was off switches nothing on");
      assertEquals(35, key(root, "Hole Snap"));
   }

   @Test
   void offhandBecomesAutoTotemsItem() {
      JsonObject root = config(
         11,
         "AutoTotem", module(false, -1, "Health", "20.0", "Delay", "2.0"),
         "Offhand", module(true, 25, "Item", "\"Shield\"", "Totem Health", "8.0", "Sword Gapple", "true")
      );
      ConfigMigrations.migrate(root);
      assertFalse(root.getAsJsonObject("modules").has("Offhand"));
      assertTrue(on(root, "AutoTotem"));
      assertEquals(25, key(root, "AutoTotem"));
      assertEquals("Shield", text(root, "AutoTotem", "Item"));
      assertEquals(8.0, settings(root, "AutoTotem").get("Health").getAsDouble());
      assertTrue(settings(root, "AutoTotem").get("Sword Gapple").getAsBoolean());
   }

   @Test
   void autoTotemThatOnlySwappedAtLowHealthLeavesTheOffhandAloneUntilThen() {
      JsonObject low = config(11, "AutoTotem", module(true, -1, "Health", "6.0"));
      ConfigMigrations.migrate(low);
      assertEquals("Nothing", text(low, "AutoTotem", "Item"));
      assertEquals(6.0, settings(low, "AutoTotem").get("Health").getAsDouble());

      JsonObject always = config(11, "AutoTotem", module(true, -1, "Health", "20.0"));
      ConfigMigrations.migrate(always);
      assertFalse(settings(always, "AutoTotem").has("Item"), "20 meant always: the default Item (Totem) is right");
   }

   @Test
   void autoEatsGoldenAppleSwitchBecomesAMode() {
      JsonObject food = config(11, "AutoEat", module(true, -1, "Golden Apples", "true"));
      ConfigMigrations.migrate(food);
      assertEquals("Always", text(food, "AutoEat", "Golden Apples"));

      JsonObject never = config(11, "AutoEat", module(true, -1, "Golden Apples", "false"));
      ConfigMigrations.migrate(never);
      assertEquals("Never", text(never, "AutoEat", "Golden Apples"));

      JsonObject healing = config(
         11, "AutoEat", module(true, -1, "Golden Apples", "false", "Hunger", "14.0"), "SmartEat", module(true, 44, "Gapple Health", "9.0", "Hunger", "10.0")
      );
      ConfigMigrations.migrate(healing);
      assertEquals("Low Health", text(healing, "AutoEat", "Golden Apples"));
      assertEquals(9.0, settings(healing, "AutoEat").get("Health").getAsDouble());
      assertEquals(14.0, settings(healing, "AutoEat").get("Hunger").getAsDouble(), "Auto Eat was on, so its own hunger level stays");
      assertEquals(44, key(healing, "AutoEat"));

      JsonObject gapple = config(11, "AutoGapple", module(true, -1, "Health", "7.0"), "AutoSoup", module(false, -1));
      ConfigMigrations.migrate(gapple);
      assertTrue(on(gapple, "Auto Eat"));
      assertEquals("Low Health", text(gapple, "Auto Eat", "Golden Apples"));
      assertEquals(7.0, settings(gapple, "Auto Eat").get("Health").getAsDouble());
      assertFalse(gapple.getAsJsonObject("modules").has("AutoSoup"));
   }

   @Test
   void modulesThatBecameAModeOnlyTakeOverWhenTheSurvivorWasOff() {
      JsonObject alone = config(11, "Glide", module(true, 36, "Fall Speed", "0.2"), "Flight", module(false, -1, "Mode", "\"Creative\""));
      ConfigMigrations.migrate(alone);
      assertTrue(on(alone, "Flight"));
      assertEquals("Glide", text(alone, "Flight", "Mode"));
      assertEquals(0.2, settings(alone, "Flight").get("Fall Speed").getAsDouble());
      assertEquals(36, key(alone, "Flight"));

      JsonObject both = config(11, "Glide", module(true, -1, "Fall Speed", "0.2"), "Flight", module(true, -1, "Mode", "\"Jetpack\""));
      ConfigMigrations.migrate(both);
      assertEquals("Jetpack", text(both, "Flight", "Mode"), "Flight was on and keeps flying its own way");
      assertFalse(both.getAsJsonObject("modules").has("Glide"));

      JsonObject strafe = config(11, "Strafe", module(true, -1, "Strength", "1.2"));
      ConfigMigrations.migrate(strafe);
      assertTrue(on(strafe, "Speed"));
      assertEquals("Vanilla", text(strafe, "Speed", "Mode"));
      assertEquals(1.2, settings(strafe, "Speed").get("Multiplier").getAsDouble());
      assertTrue(settings(strafe, "Speed").get("Air Control").getAsBoolean());
   }

   @Test
   void noSlowOnlyGainsWhatWasRunningBefore() {
      JsonObject items = config(11, "NoSlow", module(true, -1, "Strength", "100.0"));
      ConfigMigrations.migrate(items);
      assertFalse(settings(items, "NoSlow").get("Cobwebs").getAsBoolean(), "NoWeb was not on, so cobwebs still slow you");
      assertFalse(settings(items, "NoSlow").get("Soul Sand And Honey").getAsBoolean());
      assertFalse(settings(items, "NoSlow").has("Items"), "the default (on) is what NoSlow did");

      JsonObject webs = config(11, "NoWeb", module(true, -1, "Cobwebs", "true", "Berry Bushes", "false", "Powder Snow", "true"));
      ConfigMigrations.migrate(webs);
      assertTrue(on(webs, "No Slow"));
      assertFalse(settings(webs, "No Slow").get("Items").getAsBoolean(), "NoSlow itself was off");
      assertTrue(settings(webs, "No Slow").get("Cobwebs").getAsBoolean());
      assertFalse(settings(webs, "No Slow").get("Berry Bushes").getAsBoolean());
   }

   @Test
   void renamesThatLandOnAnotherModulesNameMergeTheTwo() {
      JsonObject root = config(
         11,
         "Auto Drop", module(false, -1, "Mode", "\"Whitelist\"", "Delay", "4.0"),
         "InventoryCleaner", module(true, 37, "Delay Ticks", "9.0", "Drop Junk", "true"),
         "EXP Thrower", module(false, 38, "Delay", "4.0"),
         "Auto EXP", module(true, -1, "Durability %", "60.0", "Delay", "2.0", "Silent Aim", "true"),
         "Auto Web Fill", module(false, -1, "Webs Ahead", "3.0"),
         "WebAura", module(true, 39, "Enemy Range", "4.5")
      );
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      for (String gone : List.of("Auto Drop", "InventoryCleaner", "EXP Thrower", "Auto Web Fill", "WebAura")) {
         assertFalse(modules.has(gone), gone);
      }

      assertTrue(on(root, "Inventory Cleaner"));
      assertEquals(37, key(root, "Inventory Cleaner"));
      assertEquals("Keep Listed", text(root, "Inventory Cleaner", "Mode"));
      assertEquals(4.0, settings(root, "Inventory Cleaner").get("Delay").getAsDouble(), "Auto Drop's own delay stays");
      assertFalse(settings(root, "Inventory Cleaner").has("Drop Junk"));

      assertTrue(on(root, "Auto EXP"));
      assertEquals(38, key(root, "Auto EXP"));
      assertEquals(60.0, settings(root, "Auto EXP").get("Durability %").getAsDouble());
      assertEquals(4.0, settings(root, "Auto EXP").get("Delay").getAsDouble());
      assertFalse(settings(root, "Auto EXP").has("Silent Aim"));

      assertTrue(on(root, "Web Aura"));
      assertEquals(39, key(root, "Web Aura"));
      assertEquals(0.0, settings(root, "Web Aura").get("Webs Ahead").getAsDouble(), "only the feet were webbed before");
      assertTrue(settings(root, "Web Aura").get("Feet").getAsBoolean());
      assertEquals(4.5, settings(root, "Web Aura").get("Enemy Range").getAsDouble());
   }

   @Test
   void theOldModuleAloneCarriesOnUnderTheSharedName() {
      JsonObject root = config(11, "InventoryCleaner", module(true, -1, "Delay Ticks", "9.0"), "WebAura", module(false, 39, "Place Range", "4.0"));
      ConfigMigrations.migrate(root);
      assertTrue(on(root, "Inventory Cleaner"));
      assertEquals(9.0, settings(root, "Inventory Cleaner").get("Delay").getAsDouble());
      assertFalse(on(root, "Web Aura"));
      assertEquals(39, key(root, "Web Aura"));
      assertEquals(4.0, settings(root, "Web Aura").get("Place Range").getAsDouble());
      assertEquals(0.0, settings(root, "Web Aura").get("Webs Ahead").getAsDouble());
   }

   @Test
   void oneShotModulesBecomeWorldToolsButtonsWithoutSwitchingItOn() {
      JsonObject root = config(
         11,
         "World Rules", module(false, -1, "Daylight Cycle", "true"),
         "Time", module(true, 45, "Time", "\"Night\"", "Ticks", "13000.0", "Lock", "true"),
         "Weather", module(false, -1, "Weather", "\"Thunder\"", "Lock", "false"),
         "Gamemode", module(false, 46, "Mode", "\"Spectator\""),
         "Seed", module(false, -1)
      );
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      for (String gone : List.of("World Rules", "Time", "Weather", "Gamemode", "Seed")) {
         assertFalse(modules.has(gone), gone);
      }

      assertFalse(on(root, "World Tools"), "its switch applies game rules; a time button must not flip it");
      assertEquals(-1, key(root, "World Tools"));
      assertEquals("Night", text(root, "World Tools", "Time"));
      assertEquals(13000.0, settings(root, "World Tools").get("Time Ticks").getAsDouble());
      assertFalse(settings(root, "World Tools").get("Daylight Cycle").getAsBoolean(), "a locked time is the daylight rule switched off");
      assertEquals("Thunder", text(root, "World Tools", "Weather"));
      assertEquals("Spectator", text(root, "World Tools", "Game Mode"));
   }

   @Test
   void noRenderOnlyHidesWhatTheMergedModulesHid() {
      JsonObject root = config(11, "AntiBlind", module(true, -1, "Blindness", "true", "Darkness", "false", "Nausea", "true"), "NoBob", module(true, 47));
      ConfigMigrations.migrate(root);
      JsonObject noRender = settings(root, "No Render");
      assertTrue(on(root, "No Render"));
      assertEquals(47, key(root, "No Render"));
      assertTrue(noRender.get("Blindness").getAsBoolean());
      assertFalse(noRender.get("Darkness").getAsBoolean());
      assertTrue(noRender.get("Nausea").getAsBoolean());
      assertTrue(noRender.get("View Bobbing").getAsBoolean());
      assertFalse(noRender.get("Fire").getAsBoolean(), "No Render was off: its own overlays must not start hiding things");
      assertFalse(noRender.has("Hurt Shake"));

      JsonObject running = config(11, "NoRender", module(true, -1, "Fire", "true"));
      ConfigMigrations.migrate(running);
      assertTrue(settings(running, "NoRender").get("Fire").getAsBoolean());
      assertFalse(settings(running, "NoRender").get("Blindness").getAsBoolean(), "AntiBlind was not on");
   }

   @Test
   void fastBreakTakesInstantMineAndPacketMine() {
      JsonObject root = config(11, "InstantMine", module(true, 48, "Drops", "false"), "PacketMine", module(true, -1), "FastUse", module(true, -1, "Delay", "2.0"));
      ConfigMigrations.migrate(root);
      assertTrue(on(root, "Fast Break"));
      assertEquals(48, key(root, "Fast Break"));
      assertEquals("Instant", text(root, "Fast Break", "Mode"));
      assertFalse(settings(root, "Fast Break").get("Drops").getAsBoolean());
      assertTrue(settings(root, "Fast Break").get("Keep Mining").getAsBoolean());
      assertTrue(on(root, "Fast Place"));
      assertFalse(settings(root, "Fast Place").get("Blocks Only").getAsBoolean());
      assertEquals(2.0, settings(root, "Fast Place").get("Delay").getAsDouble());

      JsonObject keep = config(11, "PacketMine", module(true, -1));
      ConfigMigrations.migrate(keep);
      assertEquals(1.0, settings(keep, "Fast Break").get("Speed").getAsDouble(), "PacketMine alone did not mine faster");
      assertFalse(settings(keep, "Fast Break").get("No Delay").getAsBoolean());
   }

   @Test
   void otherMergesCarryTheirSettings() {
      JsonObject root = config(
         11,
         "SelfTrap", module(true, -1),
         "Surround", module(true, -1),
         "Auto Heal", module(true, -1, "Health", "8.0", "Mode", "\"Full Heal\"", "Cooldown", "400.0"),
         "Give List", module(false, -1, "Items", "[\"diamond\", \"stick\"]", "Count", "3.0"),
         "NoHunger", module(true, -1),
         "Auto Torch", module(true, -1, "Light Level", "3.0", "Avoid", "[\"ice\"]"),
         "Eagle", module(true, -1, "Look Ahead", "0.3", "Only Backwards", "true"),
         "AutoClicker", module(true, -1, "Require Mouse", "true", "CPS", "12.0"),
         "Auto Hop", module(true, -1)
      );
      ConfigMigrations.migrate(root);
      assertEquals("Full", text(root, "Surround", "Cover"), "feet and head were both on");
      assertEquals(8.0, settings(root, "Regen").get("Below Health").getAsDouble());
      assertEquals(200.0, settings(root, "Regen").get("Interval").getAsDouble(), "clamped to the new range");
      assertEquals(20.0, settings(root, "Regen").get("Amount").getAsDouble());
      assertEquals("Custom", text(root, "Kits", "Kit"));
      assertEquals(2, settings(root, "Kits").getAsJsonArray("Custom Items").size());
      assertEquals(3.0, settings(root, "Kits").get("Custom Count").getAsDouble());
      assertFalse(on(root, "Kits"));
      assertTrue(on(root, "Buffs"));
      assertTrue(settings(root, "Buffs").get("Saturation").getAsBoolean());
      assertEquals(3.0, settings(root, "Spawn Proofer").get("Max Light").getAsDouble());
      assertEquals(1, settings(root, "Spawn Proofer").getAsJsonArray("Avoid").size());
      assertEquals("Sneak", text(root, "Safe Walk", "Mode"));
      assertTrue(settings(root, "Safe Walk").get("Only Backwards").getAsBoolean());
      assertFalse(settings(root, "Safe Walk").has("Look Ahead"));
      assertTrue(settings(root, "Trigger Bot").get("Require Click").getAsBoolean());
      assertFalse(settings(root, "Trigger Bot").has("CPS"));
      assertEquals("Always", text(root, "Auto Jump", "Mode"));
   }

   @Test
   void removedModulesAreDeleted() {
      JsonObject root = config(11, "Teams", module(true, 20), "Stash Finder", module(false, -1), "Welcome", module(true, -1), "Timer", module(true, 21), "Bow Aimbot", module(true, 22));
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertEquals(2, modules.size(), modules.keySet().toString());
      assertEquals(21, key(root, "Timer"), "Timer is not Time");
      assertEquals(22, key(root, "Bow Aimbot"));
   }

   @Test
   void aVersion8ConfigArrivesThroughTheWholeChain() {
      // Written before the ArrayList widget (9), Bow Aimbot (10) and the version 11 renames.
      JsonObject root = config(
         8,
         "ProjectileAimbot", module(true, 23),
         "KillAura", module(true, 19, "Priority", "\"Health\"", "Version", "\"1.9+ Cooldown\"", "Smart Switch", "false"),
         "KeepY", module(true, 24),
         "AnchorPull", module(true, 26),
         "TimerRange", module(true, 27),
         "Fucker", module(true, 28),
         "ChinaHat", module(true, 29),
         "HUD", module(true, -1, "ArrayList", "true", "List Side", "\"Left\"")
      );
      ConfigMigrations.migrate(root);
      JsonObject modules = root.getAsJsonObject("modules");
      assertEquals(23, key(root, "Bow Aimbot"));
      assertEquals("Lowest Health", text(root, "KillAura", "Priority"));
      assertEquals("Cooldown", text(root, "KillAura", "Timing"));
      assertFalse(settings(root, "KillAura").get("Auto Weapon").getAsBoolean());
      assertTrue(on(root, "Auto Jump"));
      assertEquals(24, key(root, "Auto Jump"));
      assertEquals("Always", text(root, "Auto Jump", "Mode"));
      assertTrue(on(root, "Hole Snap"));
      assertEquals(26, key(root, "Hole Snap"));
      assertTrue(on(root, "Cone Hat"));
      assertEquals("Top Left", text(root, "ArrayList", "Corner"));
      for (String gone : List.of("ProjectileAimbot", "KeepY", "Auto Hop", "AnchorPull", "Hole Pull", "TimerRange", "Gap Closer", "Fucker", "Bed Breaker", "ChinaHat")) {
         assertFalse(modules.has(gone), gone);
      }

      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
   }

   @Test
   void aVersion11ConfigMigratesOnceAndThenStaysPut() {
      JsonObject root = config(
         11,
         "KillAura", module(true, 19, "Version", "\"1.8 Legacy CPS\""),
         "Offhand", module(true, -1, "Item", "\"End Crystal\""),
         "Glide", module(true, -1),
         "Time", module(false, -1, "Time", "\"Noon\""),
         "Auto Drop", module(true, -1, "Mode", "\"Blacklist\""),
         "Teams", module(true, -1)
      );
      assertFalse(ConfigMigrations.migrate(root).isEmpty());
      String once = root.toString();
      assertTrue(ConfigMigrations.migrate(root).isEmpty());
      assertEquals(once, root.toString());
      assertEquals("End Crystal", text(root, "Auto Totem", "Item"));
      assertEquals("Drop Listed", text(root, "Inventory Cleaner", "Mode"));
   }

   @Test
   void handEditedEntriesDoNotStopTheVersion12Step() {
      JsonObject root = config(
         11,
         "AutoTotem", module(true, -1, "Health", "\"lots\""),
         "AutoEat", module(true, -1, "Golden Apples", "[1, 2]"),
         "Auto Heal", module(true, -1, "Cooldown", "\"soon\"", "Mode", "7"),
         "Strafe", module(true, -1, "Strength", "true")
      );
      JsonObject modules = root.getAsJsonObject("modules");
      modules.addProperty("Offhand", "not an object");
      modules.addProperty("Flight", 3);
      modules.add("Glide", module(true, -1));
      modules.getAsJsonObject("Glide").addProperty("settings", "none");
      modules.getAsJsonObject("Glide").addProperty("keybind", "F");
      JsonObject noSettings = new JsonObject();
      noSettings.addProperty("enabled", "yes");
      modules.add("NoWeb", noSettings);
      ConfigMigrations.migrate(root);
      assertFalse(modules.has("Offhand"));
      assertFalse(modules.has("Glide"));
      assertFalse(modules.has("NoWeb"));
      assertTrue(on(root, "Flight"), "the damaged Flight entry was replaced and Glide took over");
      assertEquals("Glide", text(root, "Flight", "Mode"));
      assertFalse(settings(root, "AutoTotem").has("Item"));
      assertFalse(settings(root, "AutoEat").has("Golden Apples"), "an unreadable value falls back to the default");
      assertEquals(8.0, settings(root, "Regen").get("Amount").getAsDouble());
      assertEquals(1.0, settings(root, "Speed").get("Multiplier").getAsDouble());
      assertEquals(ConfigMigrations.CURRENT_VERSION, ConfigMigrations.versionOf(root));
   }
}
