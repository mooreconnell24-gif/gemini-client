package gemini.ui.help;

import java.util.List;

/**
 * The fixed text of the help screen (the Controls and About tabs) and the rule for opening "What's new" by
 * itself. Pure data and logic, kept apart from the screen so it is tested and easy to keep in step with the code.
 */
public final class HelpContent {
   /** One input and what it does. */
   public record Control(String input, String action) {
   }

   /** A titled table of controls. */
   public record Section(String title, List<Control> controls) {
   }

   private HelpContent() {
   }

   /**
    * Whether "What's new" should open by itself: once for every version the player has not seen yet, including the
    * very first start. {@code current} is null or blank only in a broken build, which never opens it.
    */
   public static boolean shouldShowWhatsNew(String lastSeen, String current) {
      return current != null && !current.isBlank() && !current.equals(lastSeen);
   }

   /**
    * The first {@code count} bullet points of a parsed changelog as plain text (no formatting codes), for the home
    * screen's "what's new" card. Nested points and empty ones are skipped.
    */
   public static List<String> teaser(List<Markdown.Block> blocks, int count) {
      List<String> points = new java.util.ArrayList<>();
      for (Markdown.Block block : blocks) {
         if (points.size() >= count) {
            break;
         }

         if (block instanceof Markdown.Bullet bullet && bullet.depth() == 0) {
            String text = Markdown.strip(bullet.text()).strip();
            if (!text.isEmpty()) {
               points.add(text);
            }
         }
      }

      return points;
   }

   /** The Controls tab. {@code menuKey} and {@code paletteKey} are the current bindings as shown to the player. */
   public static List<Section> controls(String menuKey, String paletteKey) {
      return List.of(
         new Section("Anywhere", List.of(
            new Control(menuKey, "Open or close the module menu (rebind it in its sidebar or in Controls)"),
            new Control(paletteKey + " (in game: the key alone)", "Quick search: modules, settings, commands and pages"),
            new Control(".help  /  .changelog", "This guide on Commands  /  on What's new")
         )),
         new Section("Quick search", List.of(
            new Control("Type", "Fuzzy search; old names and setting names find their module (\"webs ahead\" finds Web Aura)"),
            new Control("Up / Down, mouse wheel", "Move through the results"),
            new Control("Enter / click", "Toggle a module, run a page, or pre-fill a command in chat"),
            new Control("Shift + Enter", "Open the module in the module menu"),
            new Control("Tab / Shift + Tab", "Cycle result kinds: all, modules, commands, pages"),
            new Control("Esc", "Clear the search, then close")
         )),
         new Section("Module menu: Window layout (default)", List.of(
            new Control("Click a module", "Show its settings"),
            new Control("Click the switch / right-click", "Toggle it"),
            new Control("Middle-click a module", "Bind a key (Delete clears, Esc cancels)"),
            new Control("Type anything, Ctrl + F", "Search every module and setting name; Esc clears and keeps the selection"),
            new Control("Up / Down, Enter, Tab", "Move the selection, toggle it, switch category or page"),
            new Control("Right-click a setting", "Reset it to the default")
         )),
         new Section("Module menu: Panels layout", List.of(
            new Control("Left click", "Toggle a module"),
            new Control("Right click", "Open module settings"),
            new Control("Middle click", "Bind a key (Delete clears, Esc cancels)"),
            new Control("Shift + right click", "Star or unstar a favourite"),
            new Control("Type anything", "Search; Up / Down move, Enter toggles, Shift + Enter opens settings"),
            new Control("Double-click a slider", "Type an exact value")
         )),
         new Section("HUD layout editor", List.of(
            new Control("Drag (Shift + drag in the Panels layout)", "Move a HUD widget; it snaps to guides, edges and equal gaps"),
            new Control("Alt + drag", "Move without snapping"),
            new Control("Arrow keys", "Nudge the selected widget by 1 px (Shift: 10 px)")
         ))
      );
   }

   /** The About tab, one paragraph per entry; {@code version} is the running Gemini version. */
   public static List<String> about(String version, String minecraft) {
      return List.of(
         "Gemini Client " + version + " for Minecraft " + minecraft + ".",
         "Gemini is a single-player sandbox client. While sandbox mode is on, modules only run in a private single-player world, and server and Realms connections are refused with a screen that explains why. Switch sandbox mode off (title screen, or .sandbox off) and Gemini goes completely dormant so you can play online; nothing of it runs on a server.",
         "Your settings live in config/gemini_config.json (a backup is kept next to it), profiles in config/gemini_profiles and exports in config/gemini_exports. Share codes and exports leave out waypoints, your home, panel positions and the sandbox switch.",
         "Source and issues: github.com/mooreconnell24-gif/gemini-client",
         "Pathfinding is powered by a bundled Baritone build (github.com/cabaletta/baritone), with Gemini's own engine as a fallback."
      );
   }
}
