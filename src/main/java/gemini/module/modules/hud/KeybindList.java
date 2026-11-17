package gemini.module.modules.hud;

import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.List;

public final class KeybindList extends HudWidget {
   private final BooleanSetting onlyActive = this.addSetting(
      new BooleanSetting("Only Active", "Only show bound modules that are turned on", false)
   );

   public KeybindList() {
      super("Keybinds", "Shows the modules you have bound to keys and whether each is on.", "Left Center", 6, -45);
   }

   private List<Module> boundModules() {
      Gemini g = Gemini.getInstance();
      if (g == null) {
         return List.of();
      } else {
         List<Module> list = new ArrayList<>();

         for (Module m : g.modules().getModules()) {
            if (m.getKeybind() != -1 && (!this.onlyActive.get() || m.isEnabled())) {
               list.add(m);
            }
         }

         return list;
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      // Nothing bound (or, with Only Active, nothing on): no card instead of a "None bound" placeholder.
      if (this.boundModules().isEmpty()) {
         return 0;
      }

      int max = UiFont.width("Keybinds") + 28;

      for (Module m : this.boundModules()) {
         String key = "[" + GeminiKeys.displayName(m.getKeybind()) + "]";
         max = Math.max(max, UiFont.width(m.getName()) + UiFont.width(key) + 22);
      }

      return Math.max(106, max);
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      List<Module> list = this.boundModules();
      return list.isEmpty() ? 0 : 18 + list.size() * 11;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      UiFont.draw(hud.graphics, "Keybinds", 6, 4, hud.accent, hud.shadow);
      hud.graphics.fill(4, 14, w - 4, 15, GeminiMark.withAlpha(hud.accent, 90));
      List<Module> list = this.boundModules();
      if (list.isEmpty()) {
         UiFont.draw(hud.graphics, "None bound", 6, 18, -6641995, false);
      } else {
         int y = 18;

         for (Module m : list) {
            String key = "[" + GeminiKeys.displayName(m.getKeybind()) + "]";
            UiFont.draw(hud.graphics, m.getName(), 6, y, m.isEnabled() ? -1 : -6641995, hud.shadow);
            UiFont.draw(hud.graphics, key, w - 6 - UiFont.width(key), y, m.isEnabled() ? hud.accent2 : -6641995, false);
            y += 11;
         }
      }
   }
}
