package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;

public final class Fps extends Ticked {
   public Fps() {
      super("FPS", "Frames per second.", "Bottom Right", 6, 6, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int fps = hud.client.getFps();
      return List.of(new TextHudWidget.Line(fps + " FPS", fps >= 100 ? -11086174 : (fps >= 45 ? -14249 : -1548963)));
   }
}
