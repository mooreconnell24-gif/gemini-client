package gemini.module;

import gemini.ui.HudContext;
import java.util.List;

public abstract class TextHudWidget extends HudWidget {
   private static final int PAD = 4;
   private static final int LINE = 10;
   private List<TextHudWidget.Line> cached;

   protected TextHudWidget(String name, String description, String defaultCorner, int defaultX, int defaultY) {
      super(name, description, defaultCorner, defaultX, defaultY);
   }

   protected abstract List<TextHudWidget.Line> lines(HudContext var1);

   @Override
   protected int widgetWidth(HudContext hud) {
      this.cached = this.lines(hud);
      if (this.cached != null && !this.cached.isEmpty()) {
         int widest = 0;

         for (TextHudWidget.Line line : this.cached) {
            widest = Math.max(widest, hud.font.width(line.text()));
         }

         return widest + 8 + (this.hasBackground() ? 2 : 0);
      } else {
         return 0;
      }
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.cached != null && !this.cached.isEmpty() ? this.cached.size() * 10 + 8 - 2 : 0;
   }

   @Override
   protected void paint(HudContext hud, int width, int height) {
      int y = 4;
      int x = 4 + (this.hasBackground() ? 2 : 0);

      for (TextHudWidget.Line line : this.cached) {
         hud.graphics.text(hud.font, line.text(), x, y, line.color(), hud.shadow);
         y += 10;
      }
   }

   public record Line(String text, int color) {
      public static TextHudWidget.Line of(String text) {
         return new TextHudWidget.Line(text, -1511689);
      }
   }
}
