package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.modules.misc.AntiLag;
import gemini.ui.HudContext;
import java.util.List;

public final class AntiLagHud extends Ticked {
   public AntiLagHud() {
      super(
         "AntiLag Status", "Live telemetry showing FPS stability, memory usage, and total lag particles/sounds blocked by AntiLag.", "Top Right", 6, 40, 200
      );
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int fps = hud.client.getFps();
      Runtime rt = Runtime.getRuntime();
      long usedPct = rt.maxMemory() > 0L ? (rt.totalMemory() - rt.freeMemory()) * 100L / rt.maxMemory() : 0L;
      AntiLag antiLag = AntiLag.instance;
      long blocked = antiLag == null ? 0L : antiLag.totalBlocked();
      boolean active = antiLag != null && antiLag.isEnabled();
      return List.of(
         new TextHudWidget.Line("AntiLag " + (active ? "ACTIVE" : "OFF") + "  ·  " + fps + " FPS", active ? -11086174 : -6641995),
         new TextHudWidget.Line("Blocked " + blocked + " lag spikes  ·  Heap " + usedPct + "%", hud.accent)
      );
   }
}
