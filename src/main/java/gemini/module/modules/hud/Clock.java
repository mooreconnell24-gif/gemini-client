package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Clock extends Ticked {
   private final BooleanSetting real = this.addSetting(new BooleanSetting("Real Time", "Show your computer's clock", true));
   private final BooleanSetting world = this.addSetting(new BooleanSetting("World Time", "Show the in-game day and time", true));
   private final BooleanSetting twelveHour = this.addSetting(new BooleanSetting("12 Hour", "Use AM/PM for the real clock", false));

   public Clock() {
      super("Clock", "Real time and the in-game day.", "Top Left", 6, 112, 500);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      List<TextHudWidget.Line> lines = new ArrayList<>(2);
      if (this.real.get()) {
         DateTimeFormatter format = DateTimeFormatter.ofPattern(this.twelveHour.get() ? "hh:mm:ss a" : "HH:mm:ss", Locale.ENGLISH);
         lines.add(new TextHudWidget.Line(LocalTime.now().format(format), -1511689));
      }

      if (this.world.get() && hud.client.level != null) {
         long ticks = hud.client.level.getOverworldClockTime();
         long day = ticks / 24000L;
         long inDay = Math.floorMod(ticks, 24000L);
         int hour = (int)((inDay / 1000L + 6L) % 24L);
         int minute = (int)(inDay % 1000L * 60L / 1000L);
         lines.add(new TextHudWidget.Line(String.format(Locale.ROOT, "Day %d  %02d:%02d", day, hour, minute), -6641995));
      }

      return lines;
   }
}
