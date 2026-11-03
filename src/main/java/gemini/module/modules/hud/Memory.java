package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;

public final class Memory extends Ticked {
   public Memory() {
      super("Memory", "Java heap usage of the game.", "Bottom Right", 6, 78, 1000);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Runtime runtime = Runtime.getRuntime();
      long max = runtime.maxMemory() / 1048576L;
      long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
      float ratio = max <= 0L ? 0.0F : (float)used / (float)max;
      return List.of(new TextHudWidget.Line("Mem " + used + " / " + max + " MB", ratio < 0.7F ? -1511689 : (ratio < 0.9F ? -14249 : -1548963)));
   }
}
