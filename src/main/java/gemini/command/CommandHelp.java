package gemini.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The one list of Gemini's chat commands with their usage and a description. {@code .help} prints it to chat,
 * the help screen's Commands tab and the quick-search palette list it, and {@link #WORDS} (every first word the
 * dispatcher understands) is what "did you mean" suggestions are drawn from. Keeping it here means a new command
 * shows up everywhere once it is added once.
 */
public final class CommandHelp {
   /** A group of commands, in the order they are listed. */
   public enum Group {
      MODULES("Modules and settings"),
      KEYS("Key binds"),
      PROFILES("Profiles and sharing"),
      GEMINI("Gemini"),
      PLAYER("Player and movement"),
      WORLD("World"),
      BARITONE("Pathfinding (Baritone)");

      public final String label;

      Group(String label) {
         this.label = label;
      }
   }

   /**
    * One command. {@code word} is what the palette runs or pre-fills ({@code .word}); {@code usage} is the full
    * syntax as shown, which may list several forms; {@code needsWorld} marks commands that act on the world or the
    * player rather than only on Gemini's settings.
    */
   public record Entry(String word, String usage, String description, Group group, boolean needsWorld) {
   }

   public static final List<Entry> ENTRIES = List.of(
      new Entry("help", ".help [chat]", "Opens this guide on the Commands tab; .help chat prints the list to chat instead", Group.GEMINI, false),
      new Entry("changelog", ".changelog", "Opens the in-game guide on What's new", Group.GEMINI, false),
      new Entry("gui", ".gui / .menu", "Opens the menu in the layout picked in ClickGUI", Group.GEMINI, true),
      new Entry("version", ".version", "Gemini and Minecraft versions, and any Minecraft hook that failed to apply", Group.GEMINI, false),
      new Entry("sandbox", ".sandbox [on|off]", "On: Gemini active, single-player only. Off: Gemini switched off, online play allowed", Group.GEMINI, false),
      new Entry("panic", ".panic", "Turns every module off at once (also the Panic module)", Group.GEMINI, true),
      new Entry("selftest", ".selftest [run]", "Explains, then with run crash-tests every module in a throwaway world", Group.GEMINI, true),
      new Entry("reload", ".reload", "Reloads gemini_config.json from disk", Group.GEMINI, true),
      new Entry("toggle", ".toggle <module> [on|off]", "Toggles a module; .<module> on its own does the same", Group.MODULES, true),
      new Entry("modules", ".modules [category]", "Lists modules, optionally of one category", Group.MODULES, false),
      new Entry("set", ".set <module> <setting> <value>", "Changes a setting; _ stands for a space, #RRGGBB sets a colour", Group.MODULES, true),
      new Entry("set", ".set <module> <list> add|remove|toggle|set|clear|list <items>", "Edits block, item and entity lists", Group.MODULES, true),
      new Entry("get", ".get <module> [setting] / .settings", "Shows a module's settings, or one setting with its range", Group.MODULES, true),
      new Entry("reset", ".reset [<module> [setting]]", "Restores every setting, one module's, or a single setting to its default", Group.MODULES, true),
      new Entry("bind", ".bind <module> <key>", "Binds a key; warns when the key is already used by a module or a vanilla control", Group.KEYS, true),
      new Entry("unbind", ".unbind <module>", "Removes a module's key bind", Group.KEYS, true),
      new Entry("binds", ".binds / .keybinds", "Lists every bound module by key and flags keys used twice", Group.KEYS, false),
      new Entry("config", ".config save|load|delete|list|reset <name>", "Saves, loads, deletes and lists profiles in config/gemini_profiles", Group.PROFILES, false),
      new Entry("config", ".config export <name> [clipboard]", "Writes your settings to config/gemini_exports, or copies them as JSON", Group.PROFILES, false),
      new Entry("config", ".config import <name> [clipboard]", "Stores an exported file or copied JSON as a profile (load applies it)", Group.PROFILES, false),
      new Entry("config", ".config share [name]", "Copies your settings as a one-line share code (gemini:...)", Group.PROFILES, false),
      new Entry("config", ".config import code [<code>|clipboard] [name]", "Previews and stores a share code; chat is limited to 256 characters, so long codes come from the clipboard", Group.PROFILES, false),
      new Entry("vclip", ".vclip <blocks> / .hclip <blocks>", "Teleports up/down or forward by a number of blocks", Group.PLAYER, true),
      new Entry("rotate", ".rotate <yaw> [pitch]", "Turns you to an exact direction", Group.PLAYER, true),
      new Entry("drop", ".drop hand|offhand|hotbar|inventory|all", "Drops items", Group.PLAYER, true),
      new Entry("dismount", ".dismount", "Gets off whatever you are riding", Group.PLAYER, true),
      new Entry("coords", ".coords", "Copies your coordinates to the clipboard and prints them", Group.PLAYER, true),
      new Entry("say", ".say <text>", "Sends text to chat as-is, even if it starts with a dot", Group.PLAYER, true),
      new Entry("leave", ".leave [reason] / .disconnect", "Leaves the world", Group.PLAYER, true),
      new Entry("sethome", ".sethome / .home / .back", "A saved teleport in this world, and back to where you were", Group.WORLD, true),
      new Entry("gm", ".gm survival|creative|adventure|spectator", "Switches game mode (.gamemode works too)", Group.WORLD, true),
      new Entry("day", ".day / .noon / .night", "Sets the time of day", Group.WORLD, true),
      new Entry("sun", ".sun / .rain", "Sets the weather", Group.WORLD, true),
      new Entry("heal", ".heal / .feed", "Restores health or hunger", Group.WORLD, true),
      new Entry("clear", ".clear", "Clears the chat window", Group.WORLD, true),
      new Entry("baritone", "#<command> or .b <command>", "Baritone: #goto, #mine, #follow, #tunnel, #farm, #explore, #excavate, #stop", Group.BARITONE, true),
      new Entry("baritone", "#goto <x> [y] <z>", "Pathfinds to coordinates, mining and bridging on the way", Group.BARITONE, true),
      new Entry("baritone", "#mine <block> [block...]", "Finds and mines blocks, e.g. #mine diamond_ore", Group.BARITONE, true),
      new Entry("baritone", "#follow [player|players|mobs]", "Follows a player or mobs", Group.BARITONE, true),
      new Entry("baritone", "#pause / #resume / #stop / #status", "Controls the current task; #status names the engine", Group.BARITONE, true)
   );

   /** Module commands mentioned at the end of the chat help. */
   public static final String MODULE_COMMANDS = ".xray reload, .marker add|remove|list|clear, .waypoints add|remove|list|tp|clear, .<module> <setting> [value]";

   /** Every first word the dispatcher accepts, for "did you mean" suggestions. */
   public static final List<String> WORDS = List.of(
      "help", "changelog", "baritone", "bind", "unbind", "binds", "toggle", "modules", "set", "get", "settings", "reset", "panic", "config",
      "sandbox", "gui", "menu", "version", "vclip", "hclip", "dismount", "drop", "leave", "disconnect", "say", "rotate", "coords", "sethome",
      "home", "back", "gm", "gamemode", "day", "noon", "night", "sun", "rain", "heal", "feed", "clear", "reload", "selftest"
   );

   private CommandHelp() {
   }

   /** The entries of one group, in list order. */
   public static List<Entry> of(Group group) {
      List<Entry> list = new ArrayList<>();
      for (Entry entry : ENTRIES) {
         if (entry.group() == group) {
            list.add(entry);
         }
      }

      return list;
   }

   /**
    * What to pre-fill in chat for a usage line: its first form up to the first argument placeholder, with a
    * trailing space when arguments follow. ".bind &lt;module&gt; &lt;key&gt;" gives ".bind ", ".config save|load ... &lt;name&gt;"
    * gives ".config ", "#&lt;command&gt; or .b ..." gives "#".
    */
   public static String prefill(String usage) {
      String form = usage.split(" / | or ", 2)[0].strip();
      int cut = form.length();
      for (int i = 0; i < form.length(); i++) {
         char c = form.charAt(i);
         if (c == '<' || c == '[') {
            cut = i;
            break;
         }
      }

      String head = form.substring(0, cut);
      if (cut > 0 && cut < form.length() && form.charAt(cut - 1) != ' ') {
         // The placeholder is glued to the prefix ("#<command>"): the player types straight after it.
         return head;
      }

      String[] words = head.strip().split("\\s+");
      StringBuilder out = new StringBuilder();
      for (String word : words) {
         if (word.isEmpty() || word.indexOf('|') >= 0) {
            // A choice of sub-commands: stop before it so the player picks one.
            break;
         }

         out.append(word).append(' ');
      }

      String result = out.toString();
      boolean more = cut < form.length() || out.length() < head.strip().length() + 1;
      if (!more) {
         result = result.stripTrailing();
      }

      return result.isEmpty() ? head.strip() : result;
   }

   /** One chat line per entry: usage in white, description in grey. */
   public static List<String> chatLines() {
      List<String> lines = new ArrayList<>();
      lines.add("§bGemini Client §7commands (§f.help §7opens the full guide):");
      for (Group group : Group.values()) {
         lines.add("§3" + group.label);
         for (Entry entry : of(group)) {
            lines.add(" §f" + entry.usage() + " §7- " + entry.description());
         }
      }

      lines.add("§7Module commands: §f" + MODULE_COMMANDS);
      return lines;
   }
}
