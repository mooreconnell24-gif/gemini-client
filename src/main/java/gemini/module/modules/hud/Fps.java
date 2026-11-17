package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import java.util.List;

public final class Fps extends Ticked {
   public Fps() {
      super("FPS", "Shows frames per second.", "Bottom Right", 6, 6, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int fps = hud.client.getFps();
      // White while smooth; amber under 30 and red under 12 frames per second.
      return List.of(TextHudWidget.Line.row("", Integer.toString(fps), "fps", HudStyle.level(fps / 100.0F)));
   }
}
