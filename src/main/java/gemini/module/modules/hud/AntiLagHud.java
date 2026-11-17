package gemini.module.modules.hud;

import gemini.module.LiveProfiler;
import gemini.module.TextHudWidget;
import gemini.module.modules.misc.AntiLag;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;

public final class AntiLagHud extends Ticked {
   private final BooleanSetting profiler = this.addSetting(
      new BooleanSetting("Module Profiler", "List the slowest modules, in microseconds per tick or frame averaged over a second", false)
   );
   private final NumberSetting rows = this.addSetting(new NumberSetting("Profiler Rows", "Modules listed per hook", 4.0, 1.0, 12.0, 1.0).showIf(this.profiler::get));
   private final ModeSetting hook = this.addSetting(
      new ModeSetting("Profiler Hook", "Which hook to break down", "All", "All", "Tick", "World frame", "HUD frame").showIf(this.profiler::get)
   );

   public AntiLagHud() {
      super(
         "Anti Lag Status", "Shows whether AntiLag is on, your FPS, memory use, how many particles and sounds AntiLag has blocked and, optionally, the slowest modules.", "Top Right", 6, 40, 200
      );
   }

   /** Asked once a tick by the live profiler: timing runs only while this widget (or Performance) wants it. */
   public boolean wantsProfiler() {
      return this.isEnabled() && this.profiler.get();
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int fps = hud.client.getFps();
      Runtime rt = Runtime.getRuntime();
      long usedPct = rt.maxMemory() > 0L ? (rt.totalMemory() - rt.freeMemory()) * 100L / rt.maxMemory() : 0L;
      AntiLag antiLag = AntiLag.instance;
      long blocked = antiLag == null ? 0L : antiLag.totalBlocked();
      boolean active = antiLag != null && antiLag.isEnabled();
      List<TextHudWidget.Line> lines = new ArrayList<>(this.profiler.get() ? 4 + 3 * (this.rows.intValue() + 1) : 2);
      lines.add(new TextHudWidget.Line("AntiLag " + (active ? "on" : "off") + "  ·  " + fps + " FPS", active ? Glass.TEXT : Glass.TEXT_DIM));
      // AntiLag counts the particles and sounds it dropped; "lag spikes" was not what the number meant.
      lines.add(new TextHudWidget.Line(blocked > 0 ? "Blocked " + blocked + " particles and sounds  ·  Memory " + usedPct + "%" : "Memory " + usedPct + "%", Glass.TEXT_DIM));
      if (this.profiler.get()) {
         LiveProfiler.describe(lines, this.rows.intValue(), this.hook.get(), hud.accent2, Glass.TEXT, Glass.TEXT_DIM);
      }

      return lines;
   }
}
