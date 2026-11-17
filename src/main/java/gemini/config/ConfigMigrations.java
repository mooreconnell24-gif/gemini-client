package gemini.config;

import gemini.input.Keys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

/**
 * Upgrades saved configs and profiles to the current layout.
 *
 * <p>Every change to what a config means gets one numbered {@link Step}. A file records the version it was written
 * at; {@link #migrate} runs each later step in order, so a config from any older release arrives at the current
 * layout through the same path, and a new step can never be skipped or run twice. Steps only ever see the
 * {@code modules} object and must tolerate anything in it: configs are hand-edited.
 */
public final class ConfigMigrations {
   public static final int CURRENT_VERSION = 13;
   static final Set<String> REMOVED_IN_V5 = Set.of(
      "Disabler", "PingSpoof", "AntiBot", "AntiStaff", "FakeLag", "ResetVL", "Spammer", "AutoLeave", "AntiAFK", "Blink"
   );
   /** In version order. The last entry's version is {@link #CURRENT_VERSION}. */
   private static final List<Step> STEPS = List.of(
      new Step(5, ConfigMigrations::toV5),
      new Step(6, ConfigMigrations::toV6),
      new Step(7, ConfigMigrations::toV7),
      new Step(8, ConfigMigrations::toV8),
      new Step(9, ConfigMigrations::toV9),
      new Step(10, ConfigMigrations::toV10),
      new Step(11, ConfigMigrations::toV11),
      new Step(12, ConfigMigrations::toV12),
      new Step(13, ConfigMigrations::toV13)
   );
   /** Modules removed in version 12 with nothing to carry over (multiplayer-only, or a duplicate of a command or widget). */
   static final List<String> REMOVED_IN_V12 = List.of(
      "AutoPearl", "AutoRod", "Backtrack", "BlockHit", "Teams", "Freeze", "Gap Closer", "TimerRange", "Slippy", "Derp", "NoRotateSet",
      "AutoWindCharge", "BedPlates", "Bed Breaker", "Fucker", "BedDefender", "Extinguish", "LogoffSpot", "PearlTracker", "Tunnel ESP",
      "Stash Finder", "StrongholdFinder", "Auto Nametag", "AutoGG", "FlagCheck", "Notifier", "PacketLogger", "Baritone Status", "EntityCulling",
      "Coordinates", "Welcome", "Totem Counter", "Lag Notifier", "Chunk Info", "Food Info", "Xp Info", "Free Slots", "Cleanse", "Potion Spoof",
      "Tower", "BlockCounter", "Cinematic Camera", "TerrainSpeed", "Seed"
   );
   /** The HUD module's module-list settings that moved to the ArrayList widget in version 9. */
   static final List<String> OLD_LIST_SETTINGS = List.of(
      "ArrayList", "List Side", "List Anchor", "Sort", "Colors", "Background", "Side Bar", "Glow Outline", "Slide Animations", "Show Keys",
      "Module Info", "Tag Style", "Lowercase", "Opacity"
   );

   private ConfigMigrations() {
   }

   /** One upgrade: brings the {@code modules} object to {@code version} and notes what it changed. */
   private record Step(int version, BiConsumer<JsonObject, List<String>> upgrade) {
   }

   public static int versionOf(JsonObject root) {
      if (root != null && root.has("version") && root.get("version").isJsonPrimitive()) {
         try {
            return root.get("version").getAsInt();
         } catch (RuntimeException e) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   /** Upgrades the config in place and returns a line per change made, for the log. Newer and current files are left alone. */
   public static List<String> migrate(JsonObject root) {
      List<String> notes = new ArrayList<>();
      int from = versionOf(root);
      if (root == null || from >= CURRENT_VERSION) {
         return notes;
      }

      JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : null;
      if (modules != null) {
         for (Step step : STEPS) {
            if (from < step.version()) {
               step.upgrade().accept(modules, notes);
            }
         }
      }

      root.addProperty("version", CURRENT_VERSION);
      return notes;
   }

   /**
    * The module list drawn by the HUD module became the "ArrayList" HUD widget. Its on/off switch, corner and
    * options move over (renamed where the widget names them differently), and the old settings leave the HUD module.
    * A config that already has an ArrayList widget keeps it as it is, so nobody ends up with two lists.
    */
   /** Plain names for style values: the watermark styles, the ArrayList "Astolfo" colours and two menu themes. */
   private static void toV11(JsonObject modules, List<String> notes) {
      remapMode(modules, "HUD", "Watermark Style", Set.of("Nextgen Card"), "Card", notes);
      remapMode(modules, "HUD", "Watermark Style", Set.of("LiquidBounce Pill"), "Compact", notes);
      remapMode(modules, "HUD", "Watermark Style", Set.of("Minimal"), "Text", notes);
      remapMode(modules, "ArrayList", "Colors", Set.of("Astolfo"), "Pink-Blue Wave", notes);
      remapMode(modules, "ClickGUI", "Theme", Set.of("LiquidBounce"), "Blue", notes);
      remapMode(modules, "ClickGUI", "Theme", Set.of("Neon"), "Purple", notes);
      remapMode(modules, "Presets", "Preset", Set.of("LiquidBounce"), "General", notes);
      // Names that were crude or said something the module does not do.
      renameModule(modules, "Fucker", "Bed Breaker", notes);
      renameModule(modules, "KeepY", "Auto Hop", notes);
      renameModule(modules, "TimerRange", "Gap Closer", notes);
      renameModule(modules, "AnchorPull", "Hole Pull", notes);
      renameModule(modules, "ChinaHat", "Cone Hat", notes);
   }

   // ---- version 12: the curated module list ----

   /**
    * The module list was curated: duplicates were merged into one module each, modules that only made sense against
    * other players were removed, and names, settings and mode values got plain wording. Order matters here:
    * settings of surviving modules are renamed first (under whatever spelling the entry has), then entries move to
    * their new names, then the merged modules hand over their switch, key and settings, then the removed go.
    *
    * <p>The rule for every merge: the survivor is on if it or the merged module was on; the merged module's key
    * moves over only when the survivor has none; and the merged module's settings are taken only when it was on
    * or the survivor was not, so a module that was running keeps behaving the way it did.
    */
   /** The reworked HUD: ArrayList, watermark and Target HUD settings under their new names and values. */
   private static void toV13(JsonObject modules, List<String> notes) {
      JsonObject list = settingsOf(modules, "ArrayList");
      if (list != null) {
         String colors = stringSetting(list, "Colors");
         if ("Wave".equals(colors)) {
            putSetting(list, "Wave", new JsonPrimitive(true));
         }

         Boolean background = booleanSetting(list, "Background");
         if (background != null && !background && findKey(list, "Backdrop") == null) {
            putSetting(list, "Backdrop", new JsonPrimitive("None"));
         }
      }

      remapMode(modules, "ArrayList", "Colors", Set.of("Accent"), "Static Accent", notes);
      remapMode(modules, "ArrayList", "Colors", Set.of("Wave", "Pink-Blue Wave", "Static Gradient"), "Theme Gradient", notes);
      renameSetting(modules, "ArrayList", "Colors", "Color Mode", notes);
      remapMode(modules, "ArrayList", "Side Bar", Set.of("Auto"), "Screen Edge", notes);
      renameSetting(modules, "ArrayList", "Side Bar", "Bar", notes);
      renameSetting(modules, "ArrayList", "Show Info", "Suffix", notes);
      for (String gone : new String[]{"Background", "Gradient Start", "Gradient End", "Color Speed", "Color Spread", "Opacity", "Blur", "Outline", "Tag Style", "Show Keys", "Line Spacing", "Text Shadow", "Animations"}) {
         dropSetting(modules, "ArrayList", gone, notes);
      }

      remapMode(modules, "HUD", "Watermark Style", Set.of("Card", "Compact"), "Bar", notes);
      remapMode(modules, "HUD", "Watermark Style", Set.of("Text"), "Simple", notes);
      dropSetting(modules, "HUD", "Logo", notes);
      dropSetting(modules, "HUD", "Ping", notes);
      remapMode(modules, "Target HUD", "Style", Set.of("Modern Glass", "Neon"), "Card", notes);
   }

   private static void toV12(JsonObject modules, List<String> notes) {
      renamedSettingsV12(modules, notes);
      renamedModulesV12(modules, notes);
      mergedCombatV12(modules, notes);
      mergedMovementV12(modules, notes);
      mergedPlayerV12(modules, notes);
      mergedWorldAndRenderV12(modules, notes);
      for (String removed : REMOVED_IN_V12) {
         if (take(modules, removed) != null) {
            notes.add("Removed module " + removed);
         }
      }
   }

   private static void renamedSettingsV12(JsonObject modules, List<String> notes) {
      remapMode(modules, "Kill Aura", "Version", Set.of("1.9+ Cooldown"), "Cooldown", notes);
      remapMode(modules, "Kill Aura", "Version", Set.of("1.8 Legacy CPS"), "CPS", notes);
      renameSetting(modules, "Kill Aura", "Version", "Timing", notes);
      renameSetting(modules, "Kill Aura", "Legacy CPS", "CPS", notes);
      renameSetting(modules, "Kill Aura", "Raytrace Check", "Visible Only", notes);
      renameSetting(modules, "Kill Aura", "Smart Switch", "Auto Weapon", notes);
      renameSetting(modules, "Kill Aura", "Lag Comp", "Predict", notes);

      remapMode(modules, "Flight", "Mode", Set.of("Auto"), "Creative", notes);
      remapMode(modules, "Flight", "Mode", Set.of("Strict", "Packet", "Velocity"), "Motion", notes);
      dropSetting(modules, "Flight", "Setback Damp", notes);

      remapMode(modules, "Speed", "Mode", Set.of("Strict", "Ground"), "Vanilla", notes);
      remapMode(modules, "Speed", "Mode", Set.of("Bhop", "Strafe"), "Bunny Hop", notes);
      remapMode(modules, "Speed", "Mode", Set.of("LowHop"), "Low Hop", notes);
      remapMode(modules, "Speed", "Mode", Set.of("YPort"), "Y-Port", notes);
      renameSetting(modules, "Speed", "Air Boost", "Air Control", notes);
      dropSetting(modules, "Speed", "Setback Pause", notes);

      remapMode(modules, "No Fall", "Mode", Set.of("Spoof", "Packet"), "Normal", notes);
      remapMode(modules, "No Fall", "Mode", Set.of("MLG"), "Water Bucket", notes);
      remapMode(modules, "Velocity", "Mode", Set.of("Packet"), "Reduce", notes);
      dropSetting(modules, "Velocity", "Chance", notes);
      dropSetting(modules, "Super Knockback", "Mode", notes);
      dropSetting(modules, "Auto Tool", "Weapons", notes);

      // Scaffold: two of the old sprint styles were really a second setting each.
      JsonObject scaffold = settingsOf(modules, "Scaffold");
      String sprint = scaffold == null ? null : stringSetting(scaffold, "Sprint");
      if ("Telly".equals(sprint)) {
         putSetting(scaffold, "Keep Y", new JsonPrimitive(true));
      } else if ("Eagle".equals(sprint)) {
         putSetting(scaffold, "Safe Walk", new JsonPrimitive(true));
      }

      remapMode(modules, "Scaffold", "Sprint", Set.of("Telly", "Godbridge", "Breeze", "Normal"), "On", notes);
      remapMode(modules, "Scaffold", "Sprint", Set.of("Legit"), "Vanilla", notes);
      remapMode(modules, "Scaffold", "Sprint", Set.of("Eagle"), "Off", notes);
      remapMode(modules, "Scaffold", "Rotate", Set.of("Godbridge", "Strict"), "Silent", notes);
      remapMode(modules, "Scaffold", "Rotate", Set.of("Snap"), "Visible", notes);
      dropSetting(modules, "Scaffold", "Raycast Check", notes);

      remapMode(modules, "ESP", "Mode", Set.of("Glow + Box"), "Outline + Box", notes);
      remapMode(modules, "ESP", "Mode", Set.of("3D Box"), "Box", notes);
      remapMode(modules, "ESP", "Mode", Set.of("GlowBox"), "Soft Box", notes);
      remapMode(modules, "ESP", "Mode", Set.of("Shader Glow"), "Outline", notes);
      renameSetting(modules, "ESP", "3D Health Bar", "Health Bar", notes);
      remapMode(modules, "Fullbright", "Mode", Set.of("Effect"), "Night Vision", notes);

      remapMode(modules, "Anti Lag", "Mode", Set.of("Aggressive"), "Strict", notes);
      renameSetting(modules, "Anti Lag", "Packet Flood Guard", "Particle Burst Guard", notes);
      renameSetting(modules, "Anti Lag", "Block Explosions & Fireworks", "Hide Explosions", notes);
      renameSetting(modules, "Anti Lag", "Block Smoke & Campfires", "Hide Smoke", notes);
      renameSetting(modules, "Anti Lag", "Block Weather & Portals", "Hide Weather And Portals", notes);
   }

   private static void renamedModulesV12(JsonObject modules, List<String> notes) {
      renameModule(modules, "Auto Pot", "Auto Potion", notes);
      // "Rotation" and "Rotations" are different keys, so neither rename can pick up the other's entry.
      renameModule(modules, "Rotation", "Rotation Lock", notes);
      renameModule(modules, "Aim", "Rotations", notes);
      renameModule(modules, "Middle Click Extra", "Middle Click", notes);
      renameModule(modules, "Silent Pearl", "Quick Pearl", notes);
      renameModule(modules, "Camera Tweaks", "Camera Clip", notes);
      renameModule(modules, "Search", "Block ESP", notes);
      renameModule(modules, "Elytra Recast", "Elytra Bounce", notes);
      renameModule(modules, "DamageParticles", "Damage Numbers", notes);
      renameModule(modules, "JumpEffect", "Jump Circles", notes);
      renameModule(modules, "Unfocused CPU", "Background FPS", notes);
      renameModule(modules, "World Rules", "World Tools", notes);

      // Three renames land on a name another module had. That module's entry is lifted out first, the rename
      // happens, and the lifted entry is then merged in like any other (or put back alone under the new wording).

      // Auto Drop is the new Inventory Cleaner; the old one only had a delay and a fixed junk list.
      JsonObject oldCleaner = take(modules, "InventoryCleaner");
      boolean hadDrop = findKey(modules, "Auto Drop") != null;
      renameModule(modules, "Auto Drop", "Inventory Cleaner", notes);
      if (oldCleaner != null) {
         JsonObject cleaner = entry(modules, "Inventory Cleaner");
         carryState(oldCleaner, cleaner, true);
         if (!hadDrop) {
            copySetting(settings(oldCleaner), "Delay Ticks", settings(cleaner), "Delay");
         }

         notes.add("InventoryCleaner was merged into Inventory Cleaner");
      }

      remapMode(modules, "Inventory Cleaner", "Mode", Set.of("Blacklist"), "Drop Listed", notes);
      remapMode(modules, "Inventory Cleaner", "Mode", Set.of("Whitelist"), "Keep Listed", notes);

      // EXP Thrower is the new Auto EXP; the combat module of that name adds its durability threshold.
      JsonObject oldExp = take(modules, "Auto EXP");
      boolean hadThrower = findKey(modules, "EXP Thrower") != null;
      renameModule(modules, "EXP Thrower", "Auto EXP", notes);
      if (oldExp != null) {
         JsonObject exp = entry(modules, "Auto EXP");
         carryState(oldExp, exp, true);
         copySetting(settings(oldExp), "Durability %", settings(exp), "Durability %");
         if (!hadThrower) {
            copySetting(settings(oldExp), "Delay", settings(exp), "Delay");
         }

         notes.add("The combat Auto EXP was merged into Auto EXP");
      }

      // Auto Web Fill is the new Web Aura; the old Web Aura only webbed the target's feet.
      JsonObject oldWeb = take(modules, "WebAura");
      boolean hadFill = findKey(modules, "Auto Web Fill") != null;
      renameModule(modules, "Auto Web Fill", "Web Aura", notes);
      if (oldWeb != null) {
         JsonObject web = entry(modules, "Web Aura");
         boolean fillOn = isOn(web);
         carryState(oldWeb, web, true);
         if (!hadFill) {
            // Nothing to merge with: the old entry carries on under the same name, with its placement settings.
            for (Entry<String, JsonElement> setting : settings(oldWeb).entrySet()) {
               putSetting(settings(web), setting.getKey(), setting.getValue());
            }
         }

         if (!fillOn && (isOn(oldWeb) || !hadFill)) {
            putSetting(settings(web), "Webs Ahead", new JsonPrimitive(0.0));
            putSetting(settings(web), "Feet", new JsonPrimitive(true));
            copySetting(settings(oldWeb), "Enemy Range", settings(web), "Enemy Range");
         }

         notes.add("WebAura was merged into Web Aura");
      }
   }

   private static void mergedCombatV12(JsonObject modules, List<String> notes) {
      // Auto Totem: its old "Health" below 20 meant "leave the offhand alone until then".
      JsonObject totemSettings = settingsOf(modules, "Auto Totem");
      Double totemHealth = totemSettings == null ? null : numberSetting(totemSettings, "Health");
      if (totemHealth != null && totemHealth < 20.0 && findKey(totemSettings, "Item") == null) {
         putSetting(totemSettings, "Item", new JsonPrimitive("Nothing"));
         notes.add("Auto Totem Health below 20 -> Item Nothing");
      }

      boolean totemOn = wasOn(modules, "Auto Totem");
      JsonObject offhand = merge(modules, "Offhand", "Auto Totem", true, notes);
      if (offhand != null && (isOn(offhand) || !totemOn)) {
         JsonObject to = settings(entry(modules, "Auto Totem"));
         copySetting(settings(offhand), "Item", to, "Item");
         copySetting(settings(offhand), "Totem Health", to, "Health");
         copySetting(settings(offhand), "Sword Gapple", to, "Sword Gapple");
         copySetting(settings(offhand), "Hotbar Only", to, "Hotbar Only");
         copySetting(settings(offhand), "Delay", to, "Delay");
      }

      // Auto Eat: "Golden Apples" was a switch for eating them as food; it is now Never / Low Health / Always.
      boolean eatOn = wasOn(modules, "Auto Eat");
      JsonObject gapple = merge(modules, "AutoGapple", "Auto Eat", true, notes);
      JsonObject smartEat = merge(modules, "SmartEat", "Auto Eat", true, notes);
      merge(modules, "AutoSoup", "Auto Eat", true, notes);
      boolean healsWithApples = gapple != null && isOn(gapple) || smartEat != null && isOn(smartEat);
      JsonObject eat = settingsOf(modules, "Auto Eat");
      if (eat != null) {
         Boolean asFood = booleanSetting(eat, "Golden Apples");
         if (stringSetting(eat, "Golden Apples") == null) {
            if (asFood != null || healsWithApples) {
               String mode = asFood != null && asFood ? "Always" : (healsWithApples ? "Low Health" : "Never");
               putSetting(eat, "Golden Apples", new JsonPrimitive(mode));
               notes.add("Auto Eat Golden Apples -> " + mode);
            } else {
               String key = findKey(eat, "Golden Apples");
               if (key != null) {
                  eat.remove(key);
               }
            }
         }

         if (smartEat != null && isOn(smartEat)) {
            copySetting(settings(smartEat), "Gapple Health", eat, "Health");
            if (!eatOn) {
               copySetting(settings(smartEat), "Hunger", eat, "Hunger");
            }
         } else if (gapple != null && isOn(gapple)) {
            copySetting(settings(gapple), "Health", eat, "Health");
         }
      }

      boolean triggerOn = wasOn(modules, "Trigger Bot");
      JsonObject clicker = merge(modules, "AutoClicker", "Trigger Bot", true, notes);
      if (clicker != null && (isOn(clicker) || !triggerOn)) {
         JsonObject to = settings(entry(modules, "Trigger Bot"));
         copySetting(settings(clicker), "Require Mouse", to, "Require Click");
         copySetting(settings(clicker), "Weapon Only", to, "Weapon Only");
      }

      merge(modules, "WTap", "Super Knockback", true, notes);

      // Surround: feet (itself), head (SelfTrap), or both.
      boolean surroundOn = wasOn(modules, "Surround");
      JsonObject selfTrap = merge(modules, "SelfTrap", "Surround", true, notes);
      JsonObject blockIn = merge(modules, "BlockIn", "Surround", true, notes);
      boolean headOn = selfTrap != null && isOn(selfTrap);
      boolean boxOn = blockIn != null && isOn(blockIn);
      if (headOn || boxOn) {
         JsonObject to = settings(entry(modules, "Surround"));
         putSetting(to, "Cover", new JsonPrimitive(boxOn || surroundOn ? "Full" : "Head"));
         if (boxOn && !surroundOn && !headOn) {
            putSetting(to, "Any Block", new JsonPrimitive(true));
         }
      }
   }

   private static void mergedMovementV12(JsonObject modules, List<String> notes) {
      merge(modules, "Hole Pull", "Hole Snap", true, notes);
      merge(modules, "AnchorPull", "Hole Snap", true, notes);
      merge(modules, "EdgeJump", "Parkour", true, notes);

      for (String hop : List.of("Auto Hop", "KeepY")) {
         JsonObject old = merge(modules, hop, "Auto Jump", true, notes);
         if (old != null && isOn(old)) {
            putSetting(settings(entry(modules, "Auto Jump")), "Mode", new JsonPrimitive("Always"));
         }
      }

      boolean elytraOn = wasOn(modules, "Elytra Fly");
      JsonObject boost = merge(modules, "ElytraBoost", "Elytra Fly", true, notes);
      if (boost != null && isOn(boost) && !elytraOn) {
         JsonObject to = settings(entry(modules, "Elytra Fly"));
         putSetting(to, "Mode", new JsonPrimitive("Boost"));
         copySetting(settings(boost), "Max Speed", to, "Speed");
      }

      // Glide and Strafe became modes of modules that do more: they only take over when that module was off.
      boolean flightOn = wasOn(modules, "Flight");
      JsonObject glide = merge(modules, "Glide", "Flight", !flightOn, notes);
      if (glide != null && isOn(glide) && !flightOn) {
         JsonObject to = settings(entry(modules, "Flight"));
         putSetting(to, "Mode", new JsonPrimitive("Glide"));
         copySetting(settings(glide), "Fall Speed", to, "Fall Speed");
      }

      boolean speedOn = wasOn(modules, "Speed");
      JsonObject strafe = merge(modules, "Strafe", "Speed", !speedOn, notes);
      if (strafe != null && isOn(strafe) && !speedOn) {
         JsonObject to = settings(entry(modules, "Speed"));
         Double strength = numberSetting(settings(strafe), "Strength");
         putSetting(to, "Mode", new JsonPrimitive("Vanilla"));
         putSetting(to, "Multiplier", new JsonPrimitive(Math.max(1.0, strength == null ? 1.0 : strength)));
         putSetting(to, "Air Control", new JsonPrimitive(true));
      }

      // No Slow now covers cobwebs and slow blocks too, each on its own switch. Whatever was not on before stays off.
      boolean noSlowOn = wasOn(modules, "No Slow");
      JsonObject terrain = findObject(modules, "TerrainSpeed");
      boolean terrainOn = terrain != null && isOn(terrain);
      JsonObject noWeb = merge(modules, "NoWeb", "No Slow", true, notes);
      boolean websOn = noWeb != null && isOn(noWeb);
      JsonObject slow = settingsOf(modules, "No Slow");
      if (slow != null && (noSlowOn || websOn)) {
         for (String block : List.of("Cobwebs", "Berry Bushes", "Powder Snow")) {
            Boolean value = websOn ? booleanSetting(settings(noWeb), block) : Boolean.FALSE;
            putSetting(slow, block, new JsonPrimitive(value == null || value));
         }

         putSetting(slow, "Soul Sand And Honey", new JsonPrimitive(terrainOn));
         if (!noSlowOn) {
            putSetting(slow, "Items", new JsonPrimitive(false));
         }
      }

      boolean spiderOn = wasOn(modules, "Spider");
      JsonObject wallClimb = merge(modules, "WallClimb", "Spider", true, notes);
      if (wallClimb != null && isOn(wallClimb) && !spiderOn) {
         copySetting(settings(wallClimb), "Climb Speed", settings(entry(modules, "Spider")), "Speed");
      }

      // Eagle is Safe Walk's Sneak mode. Its "Look Ahead" measured something else and is not carried.
      boolean safeWalkOn = wasOn(modules, "Safe Walk");
      JsonObject eagle = merge(modules, "Eagle", "Safe Walk", true, notes);
      if (eagle != null && isOn(eagle) && !safeWalkOn) {
         JsonObject to = settings(entry(modules, "Safe Walk"));
         putSetting(to, "Mode", new JsonPrimitive("Sneak"));
         copySetting(settings(eagle), "Only Backwards", to, "Only Backwards");
      }
   }

   private static void mergedPlayerV12(JsonObject modules, List<String> notes) {
      boolean regenOn = wasOn(modules, "Regen");
      JsonObject heal = merge(modules, "Auto Heal", "Regen", true, notes);
      if (heal != null && isOn(heal) && !regenOn) {
         JsonObject to = settings(entry(modules, "Regen"));
         copySetting(settings(heal), "Health", to, "Below Health");
         Double cooldown = numberSetting(settings(heal), "Cooldown");
         if (cooldown != null) {
            putSetting(to, "Interval", new JsonPrimitive(Math.max(1.0, Math.min(200.0, cooldown))));
         }

         String mode = stringSetting(settings(heal), "Mode");
         putSetting(to, "Amount", new JsonPrimitive("Full Heal".equals(mode) ? 20.0 : ("Regeneration".equals(mode) ? 2.0 : 8.0)));
      }

      // Give List is the Custom kit. Kits is a one-shot, so there is no switch to carry.
      JsonObject give = merge(modules, "Give List", "Kits", false, notes);
      if (give != null) {
         JsonObject to = settings(entry(modules, "Kits"));
         putSetting(to, "Kit", new JsonPrimitive("Custom"));
         copyAny(settings(give), "Items", to, "Custom Items");
         copySetting(settings(give), "Count", to, "Custom Count");
         if (findKey(to, "Clear First") == null) {
            copySetting(settings(give), "Clear First", to, "Clear First");
         }
      }

      JsonObject hunger = merge(modules, "NoHunger", "Buffs", true, notes);
      if (hunger != null && isOn(hunger)) {
         putSetting(settings(entry(modules, "Buffs")), "Saturation", new JsonPrimitive(true));
      }

      // Fast Break: the delay between blocks is new and on by default; a module that was already running keeps it off.
      boolean breakOn = wasOn(modules, "Fast Break");
      JsonObject instant = merge(modules, "InstantMine", "Fast Break", true, notes);
      JsonObject packetMine = merge(modules, "PacketMine", "Fast Break", true, notes);
      boolean instantOn = instant != null && isOn(instant);
      boolean keepOn = packetMine != null && isOn(packetMine);
      if (instantOn || keepOn) {
         JsonObject to = settings(entry(modules, "Fast Break"));
         if (instantOn) {
            putSetting(to, "Mode", new JsonPrimitive("Instant"));
            copySetting(settings(instant), "Drops", to, "Drops");
         } else if (!breakOn) {
            putSetting(to, "Speed", new JsonPrimitive(1.0));
         }

         if (keepOn) {
            putSetting(to, "Keep Mining", new JsonPrimitive(true));
         }
      }

      JsonObject fastBreak = settingsOf(modules, "Fast Break");
      if (fastBreak != null && (breakOn || instantOn || keepOn) && findKey(fastBreak, "No Delay") == null) {
         putSetting(fastBreak, "No Delay", new JsonPrimitive(false));
      }

      boolean placeOn = wasOn(modules, "Fast Place");
      JsonObject fastUse = merge(modules, "Fast Use", "Fast Place", true, notes);
      if (fastUse != null && isOn(fastUse)) {
         JsonObject to = settings(entry(modules, "Fast Place"));
         putSetting(to, "Blocks Only", new JsonPrimitive(false));
         if (!placeOn) {
            copySetting(settings(fastUse), "Delay", to, "Delay");
         }
      }
   }

   private static void mergedWorldAndRenderV12(JsonObject modules, List<String> notes) {
      // World Tools: the old one-shot modules become its buttons. Its own switch applies game rules, so their
      // switches and keys are not carried; a locked time or weather becomes the matching game rule.
      JsonObject time = merge(modules, "Time", "World Tools", false, false, notes);
      if (time != null) {
         JsonObject to = settings(entry(modules, "World Tools"));
         copySetting(settings(time), "Time", to, "Time");
         copySetting(settings(time), "Ticks", to, "Time Ticks");
         if (Boolean.TRUE.equals(booleanSetting(settings(time), "Lock")) && isOn(time)) {
            putSetting(to, "Daylight Cycle", new JsonPrimitive(false));
         }
      }

      JsonObject weather = merge(modules, "Weather", "World Tools", false, false, notes);
      if (weather != null) {
         JsonObject to = settings(entry(modules, "World Tools"));
         copySetting(settings(weather), "Weather", to, "Weather");
         if (Boolean.TRUE.equals(booleanSetting(settings(weather), "Lock")) && isOn(weather)) {
            putSetting(to, "Weather Cycle", new JsonPrimitive(false));
         }
      }

      JsonObject gamemode = merge(modules, "Gamemode", "World Tools", false, false, notes);
      if (gamemode != null) {
         copySetting(settings(gamemode), "Mode", settings(entry(modules, "World Tools")), "Game Mode");
      }

      boolean prooferOn = wasOn(modules, "Spawn Proofer");
      JsonObject torch = merge(modules, "Auto Torch", "Spawn Proofer", true, notes);
      if (torch != null && (isOn(torch) || !prooferOn)) {
         JsonObject to = settings(entry(modules, "Spawn Proofer"));
         copySetting(settings(torch), "Light Level", to, "Max Light");
         copyAny(settings(torch), "Avoid", to, "Avoid");
      }

      JsonObject animations = merge(modules, "Animations", "View Model", true, notes);
      if (animations != null && isOn(animations)) {
         copySetting(settings(animations), "Swing Duration", settings(entry(modules, "View Model")), "Swing Duration");
      }

      // No Render took in three small modules. Its own overlays are on by default, so when it was not running
      // they are switched off first: only what the merged modules did is turned on.
      boolean noRenderOn = wasOn(modules, "No Render");
      JsonObject antiBlind = merge(modules, "AntiBlind", "No Render", true, notes);
      JsonObject noBob = merge(modules, "NoBob", "No Render", true, notes);
      JsonObject noHurtCam = merge(modules, "NoHurtCam", "No Render", true, notes);
      boolean blindOn = antiBlind != null && isOn(antiBlind);
      boolean bobOn = noBob != null && isOn(noBob);
      boolean hurtOn = noHurtCam != null && isOn(noHurtCam);
      JsonObject noRender = settingsOf(modules, "No Render");
      if (noRender != null && (noRenderOn || blindOn || bobOn || hurtOn)) {
         if (!noRenderOn) {
            for (String overlay : List.of(
               "Fire", "Water Overlay", "Wall Overlay", "Pumpkin Overlay", "Powder Snow Overlay", "Portal Overlay", "Nausea", "Vignette", "Totem Animation"
            )) {
               putSetting(noRender, overlay, new JsonPrimitive(false));
            }
         }

         for (String effect : List.of("Blindness", "Darkness")) {
            Boolean value = blindOn ? booleanSetting(settings(antiBlind), effect) : Boolean.FALSE;
            putSetting(noRender, effect, new JsonPrimitive(value == null || value));
         }

         if (blindOn && !Boolean.FALSE.equals(booleanSetting(settings(antiBlind), "Nausea"))) {
            putSetting(noRender, "Nausea", new JsonPrimitive(true));
         }

         if (bobOn) {
            putSetting(noRender, "View Bobbing", new JsonPrimitive(true));
         }

         if (hurtOn) {
            putSetting(noRender, "Hurt Shake", new JsonPrimitive(true));
         }
      }

      boolean crumbsOn = wasOn(modules, "Breadcrumbs");
      JsonObject trails = merge(modules, "PlayerTrails", "Breadcrumbs", true, notes);
      if (trails != null && isOn(trails) && !crumbsOn) {
         JsonObject to = settings(entry(modules, "Breadcrumbs"));
         copySetting(settings(trails), "Rainbow", to, "Rainbow");
         copyAny(settings(trails), "Color", to, "Color");
         putSetting(to, "Fade", new JsonPrimitive(true));
      }

      boolean breakEspOn = wasOn(modules, "Break ESP");
      JsonObject indicators = merge(modules, "BreakIndicators", "Break ESP", true, notes);
      if (indicators != null && isOn(indicators) && !breakEspOn) {
         copySetting(settings(indicators), "Percentage", settings(entry(modules, "Break ESP")), "Labels");
      }
   }

   // ---- helpers for merges ----

   /** As {@link #merge(JsonObject, String, String, boolean, boolean, List)}, carrying the key bind. */
   private static JsonObject merge(JsonObject modules, String oldName, String newName, boolean carryEnabled, List<String> notes) {
      return merge(modules, oldName, newName, carryEnabled, true, notes);
   }

   /**
    * Takes {@code oldName}'s entry out and folds its state into {@code newName}'s (created when missing): the
    * survivor is switched on if the old module was on and {@code carryEnabled}, and takes the old key bind if it
    * has none and {@code carryKey}. Returns the removed entry so the caller can move settings, or null when there
    * was none.
    */
   private static JsonObject merge(JsonObject modules, String oldName, String newName, boolean carryEnabled, boolean carryKey, List<String> notes) {
      JsonObject old = take(modules, oldName);
      if (old == null) {
         return null;
      }

      JsonObject target = entry(modules, newName);
      if (carryEnabled && isOn(old)) {
         target.addProperty("enabled", true);
      }

      if (carryKey) {
         carryKey(old, target);
      }

      notes.add(oldName + " was merged into " + newName);
      return old;
   }

   /** The switch (when {@code enabled}) and the key bind of {@code from}, onto {@code to}. */
   private static void carryState(JsonObject from, JsonObject to, boolean enabled) {
      if (enabled && isOn(from)) {
         to.addProperty("enabled", true);
      }

      carryKey(from, to);
   }

   private static void carryKey(JsonObject from, JsonObject to) {
      if (!isBound(to) && isBound(from)) {
         to.add("keybind", from.get("keybind"));
      }
   }

   /** Removes a module's entry and returns it; null when there is none or it is not an object (it is removed either way). */
   private static JsonObject take(JsonObject modules, String name) {
      String key = findKey(modules, name);
      if (key == null) {
         return null;
      }

      JsonElement removed = modules.remove(key);
      return removed != null && removed.isJsonObject() ? removed.getAsJsonObject() : null;
   }

   private static JsonObject findObject(JsonObject modules, String name) {
      String key = findKey(modules, name);
      return key != null && modules.get(key).isJsonObject() ? modules.getAsJsonObject(key) : null;
   }

   /** A module's entry, created (off, unbound, no settings) when missing or damaged. */
   private static JsonObject entry(JsonObject modules, String name) {
      JsonObject found = findObject(modules, name);
      if (found != null) {
         return found;
      }

      String key = findKey(modules, name);
      if (key != null) {
         modules.remove(key);
      }

      JsonObject created = new JsonObject();
      created.addProperty("id", normalizeId(name));
      created.addProperty("enabled", false);
      created.addProperty("keybind", -1);
      created.add("settings", new JsonObject());
      modules.add(name, created);
      return created;
   }

   /** A module entry's settings object, created when missing or damaged. */
   private static JsonObject settings(JsonObject module) {
      if (module.has("settings") && module.get("settings").isJsonObject()) {
         return module.getAsJsonObject("settings");
      }

      JsonObject created = new JsonObject();
      module.add("settings", created);
      return created;
   }

   private static boolean wasOn(JsonObject modules, String name) {
      JsonObject module = findObject(modules, name);
      return module != null && isOn(module);
   }

   private static Double numberSetting(JsonObject settings, String name) {
      String key = findKey(settings, name);
      if (key == null || !settings.get(key).isJsonPrimitive() || !settings.get(key).getAsJsonPrimitive().isNumber()) {
         return null;
      }

      try {
         return settings.get(key).getAsDouble();
      } catch (RuntimeException e) {
         return null;
      }
   }

   /** Like {@link #copySetting} for values that are not a single primitive (lists, colours). */
   private static void copyAny(JsonObject from, String name, JsonObject to, String newName) {
      String key = findKey(from, name);
      if (key != null && !from.get(key).isJsonNull()) {
         putSetting(to, newName, from.get(key).deepCopy());
      }
   }

   /** Renames one setting of a module, unless it already has one under the new name. */
   private static void renameSetting(JsonObject modules, String module, String oldName, String newName, List<String> notes) {
      JsonObject settings = settingsOf(modules, module);
      String key = settings == null ? null : findKey(settings, oldName);
      if (key != null) {
         JsonElement value = settings.remove(key);
         if (findKey(settings, newName) == null) {
            settings.add(newName, value);
         }

         notes.add(module + " " + oldName + " -> " + newName);
      }
   }

   /** Moves a module's saved entry to its new name, unless an entry under the new name already exists. */
   private static void renameModule(JsonObject modules, String oldName, String newName, List<String> notes) {
      String oldKey = findKey(modules, oldName);
      if (oldKey == null || findKey(modules, newName) != null) {
         return;
      }

      JsonElement moved = modules.remove(oldKey);
      if (moved.isJsonObject() && moved.getAsJsonObject().has("id")) {
         moved.getAsJsonObject().addProperty("id", normalizeId(newName));
      }

      modules.add(newName, moved);
      notes.add(oldName + " -> " + newName);
   }

   /**
    * ProjectileAimbot became "Bow Aimbot" (its key, switch and settings move over unless a Bow Aimbot entry already
    * exists), and KillAura's Priority takes the shared targeting modes: Distance → Closest, Health → Lowest Health,
    * Angle → Closest To Crosshair, Armor (it sorted the least armoured first) → Least Armor.
    */
   private static void toV10(JsonObject modules, List<String> notes) {
      String oldKey = findKey(modules, "ProjectileAimbot");
      if (oldKey != null && findKey(modules, "Bow Aimbot") == null) {
         JsonElement moved = modules.remove(oldKey);
         modules.add("Bow Aimbot", moved);
         if (moved.isJsonObject() && moved.getAsJsonObject().has("id")) {
            moved.getAsJsonObject().addProperty("id", normalizeId("Bow Aimbot"));
         }

         notes.add("ProjectileAimbot -> Bow Aimbot");
      }

      remapMode(modules, "KillAura", "Priority", Set.of("Distance"), "Closest", notes);
      remapMode(modules, "KillAura", "Priority", Set.of("Health"), "Lowest Health", notes);
      remapMode(modules, "KillAura", "Priority", Set.of("Angle"), "Closest To Crosshair", notes);
      remapMode(modules, "KillAura", "Priority", Set.of("Armor"), "Least Armor", notes);
      // KillAura's own Highlight box became the shared Target Box; switched off stays off.
      JsonObject aura = settingsOf(modules, "KillAura");
      if (aura != null) {
         Boolean highlight = booleanSetting(aura, "Highlight");
         if (highlight != null && !highlight && findKey(aura, "Target Box") == null) {
            putSetting(aura, "Target Box", new JsonPrimitive("Off"));
            notes.add("KillAura Highlight off -> Target Box Off");
         }

         dropSetting(modules, "KillAura", "Highlight", notes);
      }
   }

   private static void toV9(JsonObject modules, List<String> notes) {
      JsonObject hudSettings = settingsOf(modules, "HUD");
      if (hudSettings == null) {
         return;
      }

      boolean any = false;
      for (String name : OLD_LIST_SETTINGS) {
         any |= findKey(hudSettings, name) != null;
      }

      if (!any) {
         return;
      }

      if (findKey(modules, "ArrayList") == null) {
         JsonObject widget = new JsonObject();
         JsonObject settings = new JsonObject();
         widget.add("settings", settings);
         Boolean on = booleanSetting(hudSettings, "ArrayList");
         boolean enabled = on == null || on;
         widget.addProperty("enabled", enabled);
         widget.addProperty("keybind", -1);
         boolean left = "Left".equals(stringSetting(hudSettings, "List Side"));
         boolean bottom = "Bottom".equals(stringSetting(hudSettings, "List Anchor"));
         settings.addProperty("Corner", (bottom ? "Bottom " : "Top ") + (left ? "Left" : "Right"));
         settings.addProperty("Offset X", 0.0);
         settings.addProperty("Offset Y", 3.0);
         String sort = stringSetting(hudSettings, "Sort");
         if (sort != null) {
            settings.addProperty("Sort", "Length".equals(sort) ? "Width" : sort);
         }

         String colors = stringSetting(hudSettings, "Colors");
         if (colors != null) {
            String renamed = colors;
            if ("Pulsing Rainbow".equals(colors)) {
               renamed = "Rainbow";
            } else if ("Neon Wave".equals(colors)) {
               renamed = "Wave";
            } else if ("Gradient".equals(colors)) {
               renamed = "Theme Gradient";
            }

            settings.addProperty("Colors", renamed);
         }

         Boolean bar = booleanSetting(hudSettings, "Side Bar");
         if (bar != null) {
            settings.addProperty("Side Bar", bar ? "Auto" : "None");
         }

         copySetting(hudSettings, "Background", settings, "Background");
         copySetting(hudSettings, "Glow Outline", settings, "Outline");
         copySetting(hudSettings, "Slide Animations", settings, "Animations");
         copySetting(hudSettings, "Show Keys", settings, "Show Keys");
         copySetting(hudSettings, "Module Info", settings, "Show Info");
         copySetting(hudSettings, "Tag Style", settings, "Tag Style");
         copySetting(hudSettings, "Lowercase", settings, "Lowercase");
         copySetting(hudSettings, "Opacity", settings, "Opacity");
         copySetting(hudSettings, "Text Shadow", settings, "Text Shadow");
         modules.add("ArrayList", widget);
         notes.add("The HUD module list became the ArrayList widget (" + (enabled ? "on" : "off") + ", " + settings.get("Corner").getAsString() + ")");
      } else {
         notes.add("Dropped the HUD module's old module-list settings; the existing ArrayList widget is kept");
      }

      for (String name : OLD_LIST_SETTINGS) {
         String key = findKey(hudSettings, name);
         if (key != null) {
            hudSettings.remove(key);
         }
      }
   }

   private static Boolean booleanSetting(JsonObject settings, String name) {
      String key = findKey(settings, name);
      if (key == null || !settings.get(key).isJsonPrimitive()) {
         return null;
      }

      try {
         return settings.get(key).getAsBoolean();
      } catch (RuntimeException e) {
         return null;
      }
   }

   /**
    * ReverseStep became the "Ledges" mode of Fast Fall. Its switch, key bind and two settings move over; when both
    * modules were on, Fast Fall runs in "Both".
    */
   private static void toV8(JsonObject modules, List<String> notes) {
      String oldKey = findKey(modules, "ReverseStep");
      if (oldKey == null) {
         return;
      }

      JsonElement old = modules.remove(oldKey);
      if (old == null || !old.isJsonObject()) {
         return;
      }

      JsonObject from = old.getAsJsonObject();
      String newKey = findKey(modules, "Fast Fall");
      JsonObject to;
      if (newKey != null && modules.get(newKey).isJsonObject()) {
         to = modules.getAsJsonObject(newKey);
      } else {
         to = new JsonObject();
         modules.add("Fast Fall", to);
      }

      JsonObject settings;
      if (to.has("settings") && to.get("settings").isJsonObject()) {
         settings = to.getAsJsonObject("settings");
      } else {
         settings = new JsonObject();
         to.add("settings", settings);
      }

      JsonObject oldSettings = from.has("settings") && from.get("settings").isJsonObject() ? from.getAsJsonObject("settings") : new JsonObject();
      copySetting(oldSettings, "Height", settings, "Ledge Height");
      copySetting(oldSettings, "Speed", settings, "Ledge Speed");
      boolean reverseOn = isOn(from);
      boolean fastOn = isOn(to);
      if (reverseOn) {
         putSetting(settings, "When", new JsonPrimitive(fastOn ? "Both" : "Ledges"));
         to.addProperty("enabled", true);
      }

      if (!isBound(to) && from.has("keybind") && from.get("keybind").isJsonPrimitive()) {
         to.add("keybind", from.get("keybind"));
      }

      notes.add("ReverseStep was merged into Fast Fall" + (reverseOn ? " (mode " + (fastOn ? "Both" : "Ledges") + ")" : ""));
   }

   private static boolean isOn(JsonObject module) {
      try {
         return module.has("enabled") && module.get("enabled").isJsonPrimitive() && module.get("enabled").getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static boolean isBound(JsonObject module) {
      try {
         return module.has("keybind") && module.get("keybind").isJsonPrimitive() && module.get("keybind").getAsInt() > 0;
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static void copySetting(JsonObject from, String name, JsonObject to, String newName) {
      String key = findKey(from, name);
      if (key != null && from.get(key).isJsonPrimitive()) {
         putSetting(to, newName, from.get(key));
      }
   }

   /** AutoClutch was folded into Clutch: carry its on/off state and key bind over, then drop it. */
   private static void toV7(JsonObject modules, List<String> notes) {
      String oldKey = findKey(modules, "AutoClutch");
      if (oldKey == null) {
         return;
      }

      JsonElement old = modules.remove(oldKey);
      if (old != null && old.isJsonObject()) {
         JsonObject from = old.getAsJsonObject();
         String newKey = findKey(modules, "Clutch");
         JsonObject to;
         if (newKey != null && modules.get(newKey).isJsonObject()) {
            to = modules.getAsJsonObject(newKey);
         } else {
            to = new JsonObject();
            modules.add("Clutch", to);
         }

         if (isOn(from)) {
            to.addProperty("enabled", true);
         }

         if (!isBound(to) && from.has("keybind") && from.get("keybind").isJsonPrimitive()) {
            to.add("keybind", from.get("keybind"));
         }
      }

      notes.add("AutoClutch was merged into Clutch");
   }

   /** Minecraft 26.3 moved from GLFW key codes to SDL scancodes; convert saved module keybinds. */
   private static void toV6(JsonObject modules, List<String> notes) {
      int converted = 0;

      for (Entry<String, JsonElement> entry : modules.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            JsonObject module = entry.getValue().getAsJsonObject();
            if (module.has("keybind") && module.get("keybind").isJsonPrimitive()) {
               try {
                  int old = module.get("keybind").getAsInt();
                  if (old > 0) {
                     module.addProperty("keybind", Keys.fromGlfw(old));
                     converted++;
                  }
               } catch (RuntimeException ignored) {
               }
            }
         }
      }

      if (converted > 0) {
         notes.add("Converted " + converted + " keybinds to Minecraft 26.3 key codes");
      }
   }

   private static void toV5(JsonObject modules, List<String> notes) {
      for (String removed : REMOVED_IN_V5) {
         String key = findKey(modules, removed);
         if (key != null) {
            modules.remove(key);
            notes.add("Removed module " + removed + " (not available in single-player builds)");
         }
      }

      remapMode(modules, "Speed", "Mode", Set.of("Vulcan", "Verus", "GrimCollide"), "LowHop", notes);
      remapMode(modules, "Flight", "Mode", Set.of("VulcanGlide"), "Glide", notes);
      remapMode(modules, "Scaffold", "Tower", Set.of("Bypass"), "Motion", notes);
      remapMode(modules, "KillAura", "Version", Set.of("Auto"), "1.9+ Cooldown", notes);
      dropSetting(modules, "Flight", "Anti Kick", notes);
      dropSetting(modules, "Boat Fly", "Anti Kick", notes);
      dropSetting(modules, "Spider", "Mode", notes);
      dropSetting(modules, "KillAura", "Bypass Mode", notes);
      JsonObject velocity = settingsOf(modules, "Velocity");
      String velocityMode = velocity == null ? null : stringSetting(velocity, "Mode");
      if ("Grim".equals(velocityMode)) {
         putSetting(velocity, "Mode", new JsonPrimitive("Jump"));
         putSetting(velocity, "Horizontal", new JsonPrimitive(0.0));
         putSetting(velocity, "Vertical", new JsonPrimitive(100.0));
         notes.add("Velocity mode Grim -> Jump (0% horizontal, 100% vertical)");
      } else if ("Vulcan".equals(velocityMode)) {
         putSetting(velocity, "Mode", new JsonPrimitive("Packet"));
         putSetting(velocity, "Horizontal", new JsonPrimitive(45.0));
         putSetting(velocity, "Vertical", new JsonPrimitive(55.0));
         notes.add("Velocity mode Vulcan -> Packet (45% horizontal, 55% vertical)");
      }
   }

   private static void remapMode(JsonObject modules, String module, String setting, Set<String> oldValues, String replacement, List<String> notes) {
      JsonObject settings = settingsOf(modules, module);
      String current = settings == null ? null : stringSetting(settings, setting);
      if (current != null && oldValues.contains(current)) {
         putSetting(settings, setting, new JsonPrimitive(replacement));
         notes.add(module + " " + setting + " " + current + " -> " + replacement);
      }
   }

   private static void dropSetting(JsonObject modules, String module, String setting, List<String> notes) {
      JsonObject settings = settingsOf(modules, module);
      String key = settings == null ? null : findKey(settings, setting);
      if (key != null) {
         settings.remove(key);
         notes.add("Removed setting " + module + "." + setting);
      }
   }

   private static JsonObject settingsOf(JsonObject modules, String module) {
      String key = findKey(modules, module);
      if (key != null && modules.get(key).isJsonObject()) {
         JsonObject moduleObject = modules.getAsJsonObject(key);
         return moduleObject.has("settings") && moduleObject.get("settings").isJsonObject() ? moduleObject.getAsJsonObject("settings") : null;
      } else {
         return null;
      }
   }

   private static String stringSetting(JsonObject settings, String name) {
      String key = findKey(settings, name);
      if (key == null) {
         return null;
      } else {
         JsonElement value = settings.get(key);
         return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
      }
   }

   private static void putSetting(JsonObject settings, String name, JsonElement value) {
      String key = findKey(settings, name);
      settings.add(key == null ? name : key, value);
   }

   public static String normalizeId(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   static String findKey(JsonObject object, String name) {
      if (object.has(name)) {
         return name;
      } else {
         String wanted = normalizeId(name);

         for (Entry<String, JsonElement> entry : object.entrySet()) {
            if (normalizeId(entry.getKey()).equals(wanted)) {
               return entry.getKey();
            }
         }

         return null;
      }
   }
}
