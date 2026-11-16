package gemini.input;

import gemini.command.Suggestions;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.ui.HudOverlay;
import gemini.ui.SandboxUi;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Proactive key-bind conflict warnings. Every path that sets a bind (panel middle-click, window rebind,
 * {@code .bind}, the menu-key rebind, a profile or import load) reports here, and a WARNING toast names whatever
 * else uses the key. Once per game session, the first time a world is entered, a single toast sums up the
 * conflicts that already exist and points at the Keybinds page.
 *
 * <p>It also keeps the one cached conflict count every surface shows (title status bar, pause dock, dashboard).
 */
public final class BindWarnings {
   private static final long COUNT_TTL_MS = 500L;
   private static long countAt;
   private static int count;
   private static boolean sessionChecked;

   private BindWarnings() {
   }

   /** The current binds of modules and vanilla controls (see {@link KeybindReport#snapshot}). */
   public static Map<String, Integer> snapshot(ModuleManager modules) {
      return KeybindReport.snapshot(modules);
   }

   /** Keys that clash with at least one module; recounted at most twice a second. */
   public static int conflictCount(ModuleManager modules) {
      long now = Util.getMillis();
      if (modules != null && (now - countAt > COUNT_TTL_MS || now < countAt)) {
         countAt = now;
         count = KeybindConflicts.moduleConflictCount(snapshot(modules), KeybindReport.CONTROLS_SUFFIX);
      }

      return count;
   }

   /** Forces the next {@link #conflictCount} to recount (after any bind change). */
   public static void invalidate() {
      countAt = 0L;
   }

   /**
    * Call after {@code module} was bound to {@code key}. Shows a WARNING toast if something else uses the key and
    * returns the warning text (null when the key is free) so the caller can also show it inline.
    */
   public static String afterBind(ModuleManager modules, Module module, int key) {
      invalidate();
      if (modules == null || module == null || !KeybindConflicts.isBound(key)) {
         return null;
      }

      String clash = KeybindReport.warning(modules, module, key);
      if (clash != null) {
         notify("Keybind conflict", module.getName() + ": " + clash);
      }

      return clash;
   }

   /** Call after the menu key was rebound; warns if a module or another control uses the new key. */
   public static String afterMenuKey(ModuleManager modules) {
      invalidate();
      if (modules == null || GeminiKeys.OPEN_GUI.isUnbound()) {
         return null;
      }

      String self = Component.translatable(GeminiKeys.OPEN_GUI.getName()).getString() + KeybindReport.CONTROLS_SUFFIX;
      Map<String, Integer> binds = snapshot(modules);
      Integer key = binds.get(self);
      if (key == null) {
         return null;
      }

      List<String> clashes = KeybindConflicts.others(binds, self, key);
      if (clashes.isEmpty()) {
         return null;
      }

      String text = GeminiKeys.displayName(key) + " is also bound to " + Suggestions.humanList(clashes);
      notify("Keybind conflict", "Menu key: " + text);
      return text;
   }

   /**
    * Call after a config, profile or import was applied, with the snapshot taken just before. Announces only the
    * conflicts the load brought in, in one toast.
    */
   public static void afterLoad(ModuleManager modules, Map<String, Integer> before, String source) {
      invalidate();
      if (modules == null || before == null) {
         return;
      }

      Map<Integer, List<String>> fresh = KeybindConflicts.introduced(before, snapshot(modules));
      if (fresh.isEmpty()) {
         return;
      }

      List<String> keys = new ArrayList<>();
      for (Map.Entry<Integer, List<String>> entry : fresh.entrySet()) {
         keys.add(GeminiKeys.displayName(entry.getKey()) + " (" + String.join(", ", entry.getValue()) + ")");
      }

      int n = fresh.size();
      notify(n == 1 ? "1 keybind conflict" : n + " keybind conflicts", source + " shares " + Suggestions.humanList(keys));
   }

   /**
    * Called every client tick. The first time per session that the player is in a world where Gemini may act,
    * one toast sums up any existing conflicts and says where to fix them.
    */
   public static void tick(Minecraft client, ModuleManager modules) {
      if (sessionChecked || client.player == null || !SingleplayerGate.allowed() || modules == null) {
         return;
      }

      sessionChecked = true;
      invalidate();
      int conflicts = conflictCount(modules);
      if (conflicts > 0) {
         HudOverlay.toast(
            conflicts == 1 ? "1 keybind conflict" : conflicts + " keybind conflicts",
            "Open the menu's Keybinds page (or .binds) to fix them",
            HudOverlay.Severity.WARNING,
            6500L
         );
      }
   }

   private static void notify(String title, String body) {
      Minecraft client = Minecraft.getInstance();
      if (client != null) {
         SandboxUi.notify(client, title, body, HudOverlay.Severity.WARNING);
      }
   }
}
