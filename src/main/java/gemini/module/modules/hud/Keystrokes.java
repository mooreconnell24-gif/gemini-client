package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import gemini.ui.HudOverlay;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

public final class Keystrokes extends HudWidget {
   private final BooleanSetting mouse = this.addSetting(new BooleanSetting("Mouse", "Show the mouse buttons", true));
   private final BooleanSetting space = this.addSetting(new BooleanSetting("Space Bar", "Show the jump key", true));
   private final float[] keyAnims = new float[7];

   public Keystrokes() {
      super("Keystrokes", "WASD, jump and mouse buttons as they are pressed.", "Left Center", 6, 40);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 72;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 48 + (this.space.get() ? 14 : 0) + (this.mouse.get() ? 22 : 0) + 2;
   }

   private void key(HudContext hud, int idx, int x, int y, int width, int height, String label, boolean down) {
      float target = down ? 1.0F : 0.0F;
      this.keyAnims[idx] = Mth.lerp(0.28F, this.keyAnims[idx], target);
      float a = this.keyAnims[idx];
      int bg = HudOverlay.lerp(-14011324, hud.accent, a);
      hud.graphics.fill(x, y, x + width, y + height, GeminiMark.withAlpha(bg, 195));
      hud.graphics.fill(x, y + height - 1, x + width, y + height, GeminiMark.withAlpha(hud.accent2, Math.round(80.0F + 175.0F * a)));
      hud.graphics.text(hud.font, label, x + (width - hud.font.width(label)) / 2, y + (height - 8) / 2, a > 0.55F ? -1 : -1511689, false);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      Options options = hud.client.options;
      int x = 4;
      int y = 4;
      this.key(hud, 0, x + 22, y, 20, 20, "W", options.keyUp.isDown());
      this.key(hud, 1, x, y + 22, 20, 20, "A", options.keyLeft.isDown());
      this.key(hud, 2, x + 22, y + 22, 20, 20, "S", options.keyDown.isDown());
      this.key(hud, 3, x + 44, y + 22, 20, 20, "D", options.keyRight.isDown());
      int below = y + 44;
      if (this.space.get()) {
         this.key(hud, 4, x, below, 64, 12, "SPACE", options.keyJump.isDown());
         below += 14;
      }

      if (this.mouse.get()) {
         this.key(hud, 5, x, below, 31, 20, "LMB", options.keyAttack.isDown());
         this.key(hud, 6, x + 33, below, 31, 20, "RMB", options.keyUse.isDown());
      }
   }
}
