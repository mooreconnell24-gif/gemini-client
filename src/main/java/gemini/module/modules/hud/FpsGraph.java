package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import net.minecraft.util.Mth;

/**
 * The frame rate of the last few seconds as a small bar graph in the theme accent, with the current value and the
 * lowest and highest of the window above it. Sampled ten times a second; the texts are rebuilt with each sample.
 */
public final class FpsGraph extends HudWidget {
   private static final int BAR = 2;
   private final int[] history = new int[48];
   private int head;
   private long lastSampleMs;
   private int max = 60;
   private String current = "0";
   private String range = "";

   public FpsGraph() {
      super("FPS Graph", "Shows a graph of recent frame rate with the lowest, highest and current FPS.", "Bottom Left", 6, 86);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 2 * HudStyle.PAD_X + this.history.length * BAR;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 2 * HudStyle.PAD_Y + HudStyle.LINE + 20;
   }

   private void sample(HudContext hud) {
      long now = System.currentTimeMillis();
      if (now - this.lastSampleMs < 100L && now >= this.lastSampleMs) {
         return;
      }

      this.lastSampleMs = now;
      int fps = Math.max(0, hud.client.getFps());
      this.history[this.head] = fps;
      this.head = (this.head + 1) % this.history.length;
      int low = Integer.MAX_VALUE;
      int high = 1;
      for (int value : this.history) {
         if (value > 0) {
            low = Math.min(low, value);
            high = Math.max(high, value);
         }
      }

      if (low == Integer.MAX_VALUE) {
         low = fps;
         high = Math.max(1, fps);
      }

      this.max = Math.max(60, high);
      this.current = Integer.toString(fps);
      this.range = low + " – " + high;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      this.sample(hud);
      int backdrop = this.backdrop();
      int x = HudStyle.PAD_X;
      int y = HudStyle.PAD_Y;
      UiFont.draw(hud.graphics, this.current, x, y, hud.text, FontSize.BODY, FontWeight.MEDIUM, hud.shadow);
      UiFont.draw(hud.graphics, "fps", x + UiFont.width(this.current, FontSize.BODY, FontWeight.MEDIUM) + HudStyle.UNIT_GAP, y, HudStyle.unit(backdrop), hud.shadow);
      UiFont.draw(hud.graphics, this.range, w - HudStyle.PAD_X - UiFont.width(this.range), y, HudStyle.label(backdrop), hud.shadow);
      int graphTop = y + HudStyle.LINE + 1;
      int graphBottom = h - HudStyle.PAD_Y;
      int graphH = graphBottom - graphTop;
      int body = Glass.withAlpha(hud.accent, 110);
      int cap = hud.accent | 0xFF000000;
      for (int i = 0; i < this.history.length; i++) {
         int value = this.history[(this.head + i) % this.history.length];
         if (value <= 0) {
            continue;
         }

         int barH = Math.max(1, Math.round(graphH * Mth.clamp((float)value / this.max, 0.0F, 1.0F)));
         int bx = x + i * BAR;
         hud.graphics.fill(bx, graphBottom - barH + 1, bx + BAR, graphBottom, body);
         hud.graphics.fill(bx, graphBottom - barH, bx + BAR, graphBottom - barH + 1, cap);
      }
   }
}
