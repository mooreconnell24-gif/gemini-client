package gemini.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The module list is curated: names are plain Title Case with spaces, no two modules share a name, and every name a
 * module used to have (before a rename or a merge) still finds the module it became, in the search and in
 * {@code .name} commands. The modules cannot be constructed without a running game, so this reads the sources: the
 * name is the first argument of the {@code super(...)} call, and old names are the {@code names.add("...")} lines of
 * {@code aliases()} plus the class name, exactly what {@link Module#aliases()} and {@link ModuleManager} use.
 */
class ModuleNamesTest {
   private static final Path MODULES = Path.of("src/main/java/gemini/module/modules");
   private static final Path MANAGER = Path.of("src/main/java/gemini/module/ModuleManager.java");
   private static final Pattern NAME = Pattern.compile("super\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
   private static final Pattern ADDED = Pattern.compile("\\w+\\.add\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)");
   private static final Pattern REGISTERED = Pattern.compile("this\\.register\\(new (\\w+)\\(");
   /** Names that are one word by convention or a product name; everything else must not run words together. */
   private static final Set<String> ONE_WORD = Set.of("ArrayList", "ClickGUI", "XCarry");
   /** Old name → the module it is found under now. Renames and merges of 3.3.0; the config migration mirrors it. */
   private static final String[][] MOVED = {
      // renamed
      {"Auto Pot", "Auto Potion"}, {"Aim", "Rotations"}, {"Rotation", "Rotation Lock"}, {"Middle Click Extra", "Middle Click"},
      {"Silent Pearl", "Quick Pearl"}, {"Camera Tweaks", "Camera Clip"}, {"Search", "Block ESP"}, {"Elytra Recast", "Elytra Bounce"},
      {"DamageParticles", "Damage Numbers"}, {"JumpEffect", "Jump Circles"}, {"Unfocused CPU", "Background FPS"}, {"World Rules", "World Tools"},
      {"Auto Drop", "Inventory Cleaner"}, {"EXP Thrower", "Auto EXP"}, {"Auto Web Fill", "Web Aura"},
      // spacing only
      {"KillAura", "Kill Aura"}, {"AutoTotem", "Auto Totem"}, {"NoFall", "No Fall"}, {"Xray", "X-Ray"}, {"StorageESP", "Storage ESP"},
      {"ChestStealer", "Chest Stealer"}, {"FastPlace", "Fast Place"}, {"AutoArmor", "Auto Armor"}, {"AutoEat", "Auto Eat"}, {"AutoTool", "Auto Tool"},
      // merged
      {"AutoClicker", "Trigger Bot"}, {"AutoGapple", "Auto Eat"}, {"AutoSoup", "Auto Eat"}, {"SmartEat", "Auto Eat"}, {"Offhand", "Auto Totem"},
      {"WebAura", "Web Aura"}, {"BlockHit", "Auto Block"}, {"WTap", "Super Knockback"}, {"SelfTrap", "Surround"}, {"BlockIn", "Surround"},
      {"Hole Pull", "Hole Snap"}, {"AnchorPull", "Hole Snap"}, {"Auto Hop", "Auto Jump"}, {"KeepY", "Auto Jump"}, {"EdgeJump", "Parkour"},
      {"ElytraBoost", "Elytra Fly"}, {"Glide", "Flight"}, {"NoWeb", "No Slow"}, {"TerrainSpeed", "No Slow"}, {"Strafe", "Speed"},
      {"WallClimb", "Spider"}, {"Eagle", "Safe Walk"}, {"Auto Heal", "Regen"}, {"InventoryCleaner", "Inventory Cleaner"}, {"Give List", "Kits"},
      {"Potion Spoof", "Buffs"}, {"NoHunger", "Buffs"}, {"Cleanse", "Buffs"}, {"Tower", "Scaffold"}, {"BlockCounter", "Scaffold"},
      {"InstantMine", "Fast Break"}, {"PacketMine", "Fast Break"}, {"Fast Use", "Fast Place"}, {"Gamemode", "World Tools"}, {"Time", "World Tools"},
      {"Weather", "World Tools"}, {"Seed", "World Tools"}, {"Auto Torch", "Spawn Proofer"}, {"Bed Breaker", "Nuker"}, {"Extinguish", "Lawn Mower"},
      {"Animations", "View Model"}, {"AntiBlind", "No Render"}, {"NoBob", "No Render"}, {"NoHurtCam", "No Render"}, {"Cinematic Camera", "Zoom"},
      {"EntityCulling", "Anti Lag"}, {"PlayerTrails", "Breadcrumbs"}, {"BreakIndicators", "Break ESP"}, {"PearlTracker", "Projectile ESP"},
      {"StrongholdFinder", "Locate"}, {"Stash Finder", "Storage ESP"}, {"Notifier", "Totem Pop Counter"}
   };

   private record Source(String name, String className, List<String> added) {
   }

   private static String key(String name) {
      return name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }

   /** Every registered module that names itself with a literal (HUD widgets and Macro slots included). */
   private static List<Source> sources() throws IOException {
      Set<String> registered = new HashSet<>();
      Matcher reg = REGISTERED.matcher(Files.readString(MANAGER));
      while (reg.find()) {
         registered.add(reg.group(1));
      }

      List<Source> out = new ArrayList<>();
      try (Stream<Path> files = Files.walk(MODULES)) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
            String className = file.getFileName().toString().replace(".java", "");
            String text = Files.readString(file);
            Matcher name = NAME.matcher(text);
            if (!registered.contains(className) || !name.find()) {
               continue;
            }

            List<String> added = new ArrayList<>();
            int at = text.indexOf("List<String> aliases()");
            if (at >= 0) {
               Matcher add = ADDED.matcher(text.substring(at, text.indexOf("\n   }", at)));
               while (add.find()) {
                  added.add(add.group(1));
               }
            }

            out.add(new Source(name.group(1), className, added));
         }
      }

      return out;
   }

   @Test
   void namesAreTitleCaseWithSpaces() throws IOException {
      List<String> problems = new ArrayList<>();
      for (Source source : sources()) {
         String name = source.name();
         if (name.equals("Macro ")) {
            continue; // "Macro " + slot
         }

         if (!ONE_WORD.contains(name) && Pattern.compile("[a-z][A-Z]").matcher(name).find()) {
            problems.add(name + ": words run together");
         }

         for (String word : name.split(" ")) {
            if (!word.isEmpty() && Character.isLowerCase(word.charAt(0))) {
               problems.add(name + ": \"" + word + "\" is not capitalised");
            }
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void theListStaysCurated() throws IOException {
      List<Source> all = sources();
      Set<String> keys = new HashSet<>();
      for (Source source : all) {
         assertTrue(keys.add(key(source.name())), "two modules are named " + source.name());
      }

      // 316 before the 3.3.0 curation. Adding a module is fine; this only stops the list drifting back unnoticed.
      assertTrue(all.size() > 200 && all.size() <= 245, "module count " + all.size());
   }

   @Test
   void oldNamesStillFindTheirModule() throws IOException {
      Map<String, String> byName = new HashMap<>();
      Map<String, String> byAlias = new HashMap<>();
      for (Source source : sources()) {
         byName.put(key(source.name()), source.name());
      }

      for (Source source : sources()) {
         String simple = source.className().endsWith("Module") ? source.className().substring(0, source.className().length() - 6) : source.className();
         byAlias.putIfAbsent(key(simple), source.name());
         for (String added : source.added()) {
            byAlias.putIfAbsent(key(added), source.name());
         }
      }

      List<String> problems = new ArrayList<>();
      for (String[] moved : MOVED) {
         String found = byName.getOrDefault(key(moved[0]), byAlias.get(key(moved[0])));
         if (!moved[1].equals(found)) {
            problems.add(moved[0] + " finds " + found + ", expected " + moved[1]);
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void theCheckItselfWorks() {
      assertEquals("killaura", key("Kill Aura"));
      assertEquals(key("X-Ray"), key("Xray"));
      assertFalse(Pattern.compile("[a-z][A-Z]").matcher("Storage ESP").find());
      assertTrue(Pattern.compile("[a-z][A-Z]").matcher("KillAura").find());
   }
}
