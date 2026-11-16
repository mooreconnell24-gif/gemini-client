package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The overworld day at a glance: day number and clock, the moon phase with tonight's slime chance, and how long
 * until night (when beds work and monsters stop burning) or morning. All of it comes from the world clock through
 * {@link DayCycle}; the widget only notices when that clock stops moving (Daylight Cycle off, Tick Freeze).
 */
public final class DayInfo extends Ticked {
   private final BooleanSetting clock = this.addSetting(new BooleanSetting("Clock", "Show the day number and the in-game time", true));
   private final BooleanSetting moon = this.addSetting(new BooleanSetting("Moon Phase", "Show tonight's moon phase", true));
   private final BooleanSetting slime = this.addSetting(
      new BooleanSetting("Slime Chance", "Add the swamp slime spawn chance the moon phase gives tonight", false).showIf(this.moon::get)
   );
   private final BooleanSetting countdown = this.addSetting(
      new BooleanSetting("Countdown", "Real time until night falls (beds work, monsters stop burning) or until morning", true)
   );
   private long lastTicks = Long.MIN_VALUE;
   private long lastMoved;

   public DayInfo() {
      super("Day Info", "Day number, moon phase, slime chance and a countdown to night or morning.", "Top Left", 6, 136, 250);
   }

   @Override
   public void onEnable() {
      this.lastTicks = Long.MIN_VALUE;
      this.lastMoved = System.currentTimeMillis();
   }

   @Override
   public void onWorldChange() {
      this.lastTicks = Long.MIN_VALUE;
      this.lastMoved = System.currentTimeMillis();
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      if (hud.client.level == null) {
         return List.of();
      }

      long ticks = hud.client.level.getOverworldClockTime();
      long now = System.currentTimeMillis();
      if (ticks != this.lastTicks) {
         this.lastTicks = ticks;
         this.lastMoved = now;
      }

      // The widget refreshes four times a second; a clock that has not moved for two seconds is stopped.
      boolean frozen = now - this.lastMoved > 2000L;
      int time = DayCycle.timeOfDay(ticks);
      int phase = DayCycle.moonPhase(ticks);
      List<TextHudWidget.Line> lines = new ArrayList<>(3);
      if (this.clock.get()) {
         lines.add(new TextHudWidget.Line("Day " + DayCycle.day(ticks) + "  " + DayCycle.clock(time) + (frozen ? "  (stopped)" : ""), Glass.TEXT));
      }

      if (this.moon.get()) {
         String text = DayCycle.phaseName(phase);
         if (this.slime.get()) {
            text = text + "  slimes " + Math.round(DayCycle.slimeChance(phase) * 100.0) + "%";
         }

         lines.add(new TextHudWidget.Line(text, phase == 0 ? Glass.WARN : Glass.TEXT_DIM));
      }

      if (this.countdown.get() && !frozen) {
         boolean night = DayCycle.isNight(time);
         String left = DayCycle.duration(DayCycle.ticksUntilChange(time));
         lines.add(night ? new TextHudWidget.Line("Night: sleep now, morning in " + left, Glass.INFO) : new TextHudWidget.Line("Night in " + left, Glass.TEXT_DIM));
      }

      return lines;
   }
}
