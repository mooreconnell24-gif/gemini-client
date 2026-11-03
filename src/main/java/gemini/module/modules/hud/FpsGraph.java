package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import net.minecraft.util.Mth;

public final class FpsGraph extends HudWidget {
   private final int[] history = new int[48];
   private int head;
   private long lastSampleMs;

   public FpsGraph() {
      super("FPS Graph", "Live sparkline graph of your framerate with min, max, and current FPS.", "Bottom Left", 6, 86);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 112;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 38;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      long now = System.currentTimeMillis();
      int currentFps = Math.max(0, hud.client.getFps());
      if (now - this.lastSampleMs >= 100L) {
         this.lastSampleMs = now;
         this.history[this.head] = currentFps;
         this.head = (this.head + 1) % this.history.length;
      }

      int min = Integer.MAX_VALUE;
      int max = 1;

      for (int v : this.history) {
         if (v > 0) {
            min = Math.min(min, v);
            max = Math.max(max, v);
         }
      }

      if (min == Integer.MAX_VALUE) {
         min = currentFps;
         max = Math.max(60, currentFps);
      }

      String title = currentFps + " FPS";
      String sub = "min " + min + " · max " + max;
      hud.graphics.text(hud.font, title, 6, 4, hud.accent, hud.shadow);
      hud.graphics.text(hud.font, sub, w - 6 - hud.font.width(sub), 4, -6641995, false);
      int graphTop = 15;
      int graphBottom = h - 4;
      int graphH = Math.max(8, graphBottom - graphTop);

      for (int i = 0; i < this.history.length; i++) {
         int val = this.history[(this.head + i) % this.history.length];
         float norm = Mth.clamp((float)val / Math.max(60, max), 0.05F, 1.0F);
         int barH = Math.max(1, Math.round(graphH * norm));
         int bx = 6 + i * 2;
         int by = graphBottom - barH;
         hud.graphics.fill(bx, by, bx + 2, graphBottom, GeminiMark.withAlpha(hud.accent, 110));
         hud.graphics.fill(bx, by, bx + 2, by + 1, hud.accent2);
      }
   }
}
