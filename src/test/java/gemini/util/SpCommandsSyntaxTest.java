package gemini.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.ParseResults;
import gemini.module.modules.world.Platform;
import gemini.module.modules.world.Summoner;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every command Gemini sends to the integrated server through {@link SpCommands}, parsed by the real 26.3 command
 * dispatcher (built offline the way vanilla's own {@code Commands.validate()} builds it). Command syntax and game rule
 * ids change between versions and a command that no longer parses fails silently in game, so this keeps them honest.
 *
 * <p>Literal commands are collected from the source; ids that modules splice into templates (effects, items,
 * enchantments, game rules, blocks, mobs) are collected from the module that uses them and each one is parsed in its
 * template.
 */
class SpCommandsSyntaxTest {
   private static final Path SOURCES = Path.of("src/main/java/gemini");
   private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
   /** Command words modules use; a lowercase literal that starts with one of these and a space is a command. */
   private static final Pattern COMMAND = Pattern.compile(
      "^(effect|give|item|enchant|gamerule|time|weather|kill|fill|summon|execute|spawnpoint|clear|difficulty|spreadplayers|gamemode|tp|save-all) .*"
   );
   private static Commands commands;
   private static CommandSourceStack source;

   @BeforeAll
   static void dispatcher() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      // INTEGRATED: the command set a single-player world has (dedicated-only commands such as save-all are missing).
      commands = new Commands(Commands.CommandSelection.INTEGRATED, Commands.createValidationContext(VanillaRegistries.createWorldLookup()));
      source = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER);
   }

   private static String problem(String command) {
      ParseResults<CommandSourceStack> parsed = commands.getDispatcher().parse(command, source);
      Exception error = Commands.getParseException(parsed);
      return error == null ? null : error.getMessage();
   }

   private static void assertAllParse(Map<String, String> commandsAndOrigin) {
      List<String> broken = new ArrayList<>();
      commandsAndOrigin.forEach((command, origin) -> {
         String problem = problem(command);
         if (problem != null) {
            broken.add(origin + ": /" + command + " -> " + problem);
         }
      });
      assertTrue(broken.isEmpty(), "commands the 26.3 server rejects:\n" + String.join("\n", broken));
   }

   private static String read(String relative) throws IOException {
      return Files.readString(SOURCES.resolve(relative));
   }

   private static List<String> groups(String text, String regex) {
      List<String> out = new ArrayList<>();
      Matcher matcher = Pattern.compile(regex).matcher(text);
      while (matcher.find()) {
         out.add(matcher.group(1));
      }

      return out;
   }

   @Test
   void theCheckRejectsWhatTheServerRejects() {
      assertTrue(problem("time set day") == null);
      assertTrue(problem("gamerule keepInventory true") != null, "camelCase rule ids are gone in 26.3");
      assertTrue(problem("save-all") != null, "save-all is dedicated-server only");
   }

   /** Complete command literals in every file that sends commands (templates ending in a space are covered below). */
   @Test
   void literalCommandsParse() throws IOException {
      Map<String, String> found = new LinkedHashMap<>();
      try (Stream<Path> files = Files.walk(SOURCES)) {
         for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
            String text = Files.readString(file);
            if (!text.contains("SpCommands.") || file.endsWith("SpCommands.java")) {
               continue;
            }

            Matcher matcher = LITERAL.matcher(text);
            while (matcher.find()) {
               String literal = matcher.group(1);
               if (COMMAND.matcher(literal).matches() && !literal.endsWith(" ") && !literal.endsWith(":") && !literal.contains("%")) {
                  found.put(literal, file.getFileName().toString());
               }
            }
         }
      }

      assertTrue(found.size() >= 25, "the scan found the literal commands (" + found.size() + ")");
      assertAllParse(found);
   }

   @Test
   void buffsEffectsParse() throws IOException {
      Map<String, String> all = new LinkedHashMap<>();
      List<String> ids = groups(read("module/modules/player/Buffs.java"), "this\\.buff\\(\\s*\"[^\"]*\",\\s*\"[^\"]*\",\\s*\"(\\w+)\"");
      assertTrue(ids.size() >= 10, "found the Buffs effects");
      for (String id : ids) {
         all.put("effect give @s minecraft:" + id + " infinite 9 true", "Buffs");
         all.put("effect clear @s minecraft:" + id, "Buffs");
      }

      all.put("effect clear @s minecraft:poison", "Cleanse");
      assertAllParse(all);
   }

   @Test
   void kitItemsParse() throws IOException {
      String kits = read("module/modules/player/Kits.java");
      Map<String, String> all = new LinkedHashMap<>();
      for (String item : groups(kits, "this\\.item\\(commands, \"(\\w+)\"")) {
         all.put("give @s minecraft:" + item + " 256", "Kits");
      }

      for (String material : groups(kits, "this\\.armour\\(commands, \"(\\w+)\"")) {
         for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            all.put("give @s minecraft:" + material + "_" + piece + " 1", "Kits");
         }

         all.put("item replace entity @s armor.head with minecraft:" + material + "_helmet", "Kits");
         all.put("item replace entity @s armor.chest with minecraft:" + material + "_chestplate", "Kits");
         all.put("item replace entity @s armor.legs with minecraft:" + material + "_leggings", "Kits");
         all.put("item replace entity @s armor.feet with minecraft:" + material + "_boots", "Kits");
      }

      for (String item : groups(kits, "this\\.offhand\\(commands, \"(\\w+)\"")) {
         all.put("item replace entity @s weapon.offhand with minecraft:" + item, "Kits");
      }

      assertTrue(all.size() > 30, "found the kit items");
      all.put("give @s minecraft:diamond 64", "Give List");
      assertAllParse(all);
   }

   @Test
   void enchanterCommandsParse() throws IOException {
      String enchanter = read("module/modules/player/Enchanter.java");
      Map<String, String> all = new LinkedHashMap<>();
      Matcher add = Pattern.compile("this\\.add\\(commands, selector, \"(\\w+)\", (\\d+)\\)").matcher(enchanter);
      while (add.find()) {
         all.put("enchant @s minecraft:" + add.group(1) + " " + add.group(2), "Enchanter");
      }

      // The ids chosen by mode: every literal returned from damageId()/protectionId() and the ternaries.
      for (String id : groups(enchanter, "-> \"(\\w+)\";")) {
         all.put("enchant @s minecraft:" + id + " 1", "Enchanter");
      }

      for (String id : groups(enchanter, "\\? \"(\\w+)\" : \"\\w+\"")) {
         all.put("enchant @s minecraft:" + id + " 1", "Enchanter");
      }

      for (String id : groups(enchanter, "\\? \"\\w+\" : \"(\\w+)\"")) {
         all.put("enchant @s minecraft:" + id + " 1", "Enchanter");
      }

      for (String slot : List.of("armor.head", "armor.chest", "armor.legs", "armor.feet")) {
         all.put("item replace entity @s weapon.mainhand from entity @s " + slot, "Enchanter");
         all.put("item replace entity @s " + slot + " from entity @s weapon.mainhand", "Enchanter");
      }

      assertTrue(all.size() > 35, "found the enchantments (" + all.size() + ")");
      assertAllParse(all);
   }

   @Test
   void worldRuleIdsParse() throws IOException {
      String rules = read("module/modules/world/WorldRules.java");
      Map<String, String> all = new LinkedHashMap<>();
      // The last literal of each rule(...) / valuedRule(...) call is the game rule id.
      for (String id : groups(rules, "this\\.(?:valued)?[rR]ule\\([^\\n]*\"(\\w+)\"\\);")) {
         all.put("gamerule " + id + " " + (id.endsWith("radius_around_player") ? "0" : "true"), "World Rules");
      }

      for (String id : groups(rules, "send\\([^;]*\"(\\w+)\"\\);")) {
         all.put("gamerule " + id + " 3", "World Rules");
      }

      assertTrue(all.size() >= 10, "found the game rules (" + all.size() + ")");
      for (String difficulty : List.of("peaceful", "easy", "normal", "hard")) {
         all.put("difficulty " + difficulty, "World Rules");
      }

      assertAllParse(all);
   }

   @Test
   void templatedCommandsParse() {
      Map<String, String> all = new LinkedHashMap<>();
      for (Setting<?> setting : new Platform().settings()) {
         if (setting instanceof ModeSetting mode && mode.name().equals("Block")) {
            for (String block : mode.modes()) {
               all.put("fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 " + Platform.blockId(block), "Platform");
               all.put("fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 minecraft:air replace " + Platform.blockId(block), "Platform");
            }
         }
      }

      for (Setting<?> setting : new Summoner().settings()) {
         if (setting instanceof ModeSetting mode && mode.name().equals("Mob")) {
            for (String mob : mode.modes()) {
               all.put(String.format(Locale.ROOT, "summon minecraft:%s %.2f %d %.2f", Summoner.entityId(mob), 1.5, 64, -2.25), "Summoner");
            }
         }
      }

      assertTrue(all.size() > 30, "found the Platform blocks and Summoner mobs");
      all.put("kill 3f2504e0-4f89-11d3-9a0c-0305e82c3301", "Mob Sweeper");
      all.put(String.format(Locale.ROOT, "execute positioned %d 0 %d run spreadplayers ~ ~ 0 1 %sfalse @s", 100, -200, ""), "Locate");
      all.put(String.format(Locale.ROOT, "execute positioned %d 0 %d run spreadplayers ~ ~ 0 1 %sfalse @s", 100, -200, "under 120 "), "Locate");
      all.put(String.format(Locale.ROOT, "execute in %s run tp @s %.3f %.3f %.3f", "minecraft:the_nether", 1.5, 64.0, -3.5), "SpCommands.teleport");
      all.put("time set " + 18000, "Time Control");
      for (String mode : List.of("survival", "creative", "adventure", "spectator")) {
         all.put("gamemode " + mode, "SpCommands.gamemode");
      }

      assertAllParse(all);
   }
}
