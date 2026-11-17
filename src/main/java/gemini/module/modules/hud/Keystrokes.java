package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import net.minecraft.client.Options;

/**
 * WASD, jump and the mouse buttons as key caps. Each cap is its own tile on the HUD backdrop (no card around the
 * group) and fills with light when pressed: quickly on the way down, a little slower on the way up, so a tap is
 * still visible.
 */
public final class Keystrokes extends HudWidget {
   private static final int KEY = 20;
   private static final int GAP = 2;
   private static final int SPACE_H = 10;
   private static final int MOUSE_H = 16;
   private static final float PRESS_SPEED = 28.0F;
   private static final float RELEASE_SPEED = 11.0F;
   private final BooleanSetting mouse = this.addSetting(new BooleanSetting("Mouse", "Show the mouse buttons", true));
   private final BooleanSetting space = this.addSetting(new BooleanSetting("Space Bar", "Show the jump key", true));
   private final float[] keyAnims = new float[7];

   public Keystrokes() {
      super("Keystrokes", "Shows WASD, jump and the mouse buttons as you press them.", "Left Center", 6, 40);
   }

   @Override
   protected boolean hasBackground() {
      // The caps are the backdrop; a card around them would box them in twice.
      return false;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 3 * KEY + 2 * GAP;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 2 * KEY + GAP + (this.space.get() ? GAP + SPACE_H : 0) + (this.mouse.get() ? GAP + MOUSE_H : 0);
   }

   private float animate(HudContext hud, int idx, boolean down) {
      float current = this.keyAnims[idx];
      float next = Glass.approach(current, down ? 1.0F : 0.0F, down ? PRESS_SPEED : RELEASE_SPEED, hud.delta);
      this.keyAnims[idx] = next;
      return next;
   }

   private void key(HudContext hud, int idx, int x, int y, int width, int height, String label, boolean down) {
      HudStyle.keycap(hud.graphics, hud.font, x, y, width, height, label, this.animate(hud, idx, down), this.backdrop(), hud.surface, hud.shadow);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      Options options = hud.client.options;
      int col = KEY + GAP;
      this.key(hud, 0, col, 0, KEY, KEY, "W", options.keyUp.isDown());
      this.key(hud, 1, 0, col, KEY, KEY, "A", options.keyLeft.isDown());
      this.key(hud, 2, col, col, KEY, KEY, "S", options.keyDown.isDown());
      this.key(hud, 3, 2 * col, col, KEY, KEY, "D", options.keyRight.isDown());
      int below = 2 * KEY + GAP + GAP;
      if (this.space.get()) {
         // A short bar stands for the space key, like the mark printed on a real one.
         float press = this.animate(hud, 4, options.keyJump.isDown());
         HudStyle.keycap(hud.graphics, hud.font, 0, below, w, SPACE_H, null, press, this.backdrop(), hud.surface, hud.shadow);
         int barColor = Glass.lerp(Glass.TEXT_DIM, 0xFF141821, press);
         int mid = below + SPACE_H / 2;
         hud.graphics.fill(w / 2 - 8, mid, w / 2 + 8, mid + 1, barColor);
         below += SPACE_H + GAP;
      }

      if (this.mouse.get()) {
         int half = (w - GAP) / 2;
         this.key(hud, 5, 0, below, half, MOUSE_H, "LMB", options.keyAttack.isDown());
         this.key(hud, 6, half + GAP, below, w - half - GAP, MOUSE_H, "RMB", options.keyUse.isDown());
      }
   }
}
