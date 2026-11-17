package gemini.module.modules.hud;

import gemini.ui.HudStyle;
import gemini.ui.Glass;
import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;

public final class Memory extends Ticked {
   public Memory() {
      super("Memory", "Shows how much memory the game is using.", "Bottom Right", 6, 78, 1000);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Runtime runtime = Runtime.getRuntime();
      long max = runtime.maxMemory() / 1048576L;
      long used = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
      float ratio = max <= 0L ? 0.0F : (float)used / (float)max;
      int color = ratio < 0.7F ? HudStyle.PRIMARY : (ratio < 0.9F ? Glass.WARN : Glass.ERROR);
      return List.of(TextHudWidget.Line.row("Memory", HudFormat.grouped(used), "/ " + HudFormat.grouped(max) + " MB", color));
   }
}
