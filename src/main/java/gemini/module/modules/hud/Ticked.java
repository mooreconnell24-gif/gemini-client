package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;

public abstract class Ticked extends TextHudWidget {
   private final int refreshMs;
   private List<TextHudWidget.Line> cache = List.of();
   private long stamp;

   public Ticked(String name, String description, String corner, int x, int y, int refreshMs) {
      super(name, description, corner, x, y);
      this.refreshMs = refreshMs;
   }

   @Override
   protected final List<TextHudWidget.Line> lines(HudContext hud) {
      long now = System.currentTimeMillis();
      if (now - this.stamp >= this.refreshMs || now < this.stamp) {
         this.cache = this.build(hud);
         this.stamp = now;
      }

      return this.cache;
   }

   protected abstract List<TextHudWidget.Line> build(HudContext var1);
}
