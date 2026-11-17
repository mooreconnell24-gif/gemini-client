package gemini.module.modules.hud;

import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

/**
 * The modules you have bound to keys: the name on the left (bright while the module is on, dim while it is off)
 * and its key on the right. The list and its texts are rebuilt a few times a second; a frame only draws.
 */
public final class KeybindList extends HudWidget {
   private final BooleanSetting onlyActive = this.addSetting(
      new BooleanSetting("Only Active", "Only show bound modules that are turned on", false)
   );
   private final List<Module> bound = new ArrayList<>();
   private final List<String> keys = new ArrayList<>();
   private int builtTick = Integer.MIN_VALUE;
   private int builtGeneration = -1;
   private int contentW;

   public KeybindList() {
      super("Keybinds", "Shows the modules you have bound to keys and whether each is on.", "Left Center", 6, -45);
   }

   /** Every fifth tick: the bound modules, their key names and the width of the widest row. */
   private void rebuild(HudContext hud) {
      LocalPlayer player = hud.client.player;
      int tick = player == null ? -1 : player.tickCount / 5;
      int generation = UiFont.generation();
      if (tick == this.builtTick && generation == this.builtGeneration) {
         return;
      }

      this.builtTick = tick;
      this.builtGeneration = generation;
      this.bound.clear();
      this.keys.clear();
      this.contentW = 0;
      Gemini gemini = Gemini.getInstance();
      if (gemini == null) {
         return;
      }

      List<Module> modules = gemini.modules().getModules();
      for (int i = 0; i < modules.size(); i++) {
         Module module = modules.get(i);
         if (module.getKeybind() != -1 && (!this.onlyActive.get() || module.isEnabled())) {
            String key = GeminiKeys.displayName(module.getKeybind());
            this.bound.add(module);
            this.keys.add(key);
            this.contentW = Math.max(this.contentW, UiFont.width(module.getName(), FontSize.BODY, FontWeight.MEDIUM) + HudStyle.LABEL_GAP + UiFont.width(key));
         }
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.rebuild(hud);
      // Nothing bound (or, with Only Active, nothing on): no card.
      return this.bound.isEmpty() ? 0 : 2 * HudStyle.PAD_X + this.contentW;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.bound.isEmpty() ? 0 : (this.bound.size() - 1) * HudStyle.LINE + HudStyle.TEXT_H + 2 * HudStyle.PAD_Y;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      int backdrop = this.backdrop();
      int off = HudStyle.unit(backdrop);
      int keyColor = HudStyle.label(backdrop);
      int y = HudStyle.PAD_Y;
      for (int i = 0; i < this.bound.size(); i++) {
         Module module = this.bound.get(i);
         boolean on = module.isEnabled();
         String key = this.keys.get(i);
         UiFont.draw(hud.graphics, module.getName(), HudStyle.PAD_X, y, on ? hud.text : off, FontSize.BODY, on ? FontWeight.MEDIUM : FontWeight.REGULAR, hud.shadow);
         UiFont.draw(hud.graphics, key, w - HudStyle.PAD_X - UiFont.width(key), y, on ? hud.accent | 0xFF000000 : keyColor, hud.shadow);
         y += HudStyle.LINE;
      }
   }
}
