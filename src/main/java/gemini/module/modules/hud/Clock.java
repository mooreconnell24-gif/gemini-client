package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Your computer's clock. The in-game day and time are Day Info's job, so the two widgets never show the same thing.
 */
public final class Clock extends Ticked {
   private final BooleanSetting twelveHour = this.addSetting(new BooleanSetting("12 Hour", "Use AM/PM instead of the 24-hour clock", false));
   private final BooleanSetting seconds = this.addSetting(new BooleanSetting("Seconds", "Show seconds", true));
   private final BooleanSetting date = this.addSetting(new BooleanSetting("Date", "Show the date before the time", false));
   private DateTimeFormatter format;
   private int formatKey = -1;

   public Clock() {
      super("Clock", "Shows the real time on your computer's clock.", "Top Left", 6, 112, 500);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int key = (this.twelveHour.get() ? 1 : 0) | (this.seconds.get() ? 2 : 0) | (this.date.get() ? 4 : 0);
      if (key != this.formatKey) {
         this.formatKey = key;
         String time = this.twelveHour.get() ? (this.seconds.get() ? "h:mm:ss a" : "h:mm a") : (this.seconds.get() ? "HH:mm:ss" : "HH:mm");
         this.format = DateTimeFormatter.ofPattern(this.date.get() ? "EEE d MMM  " + time : time, Locale.ENGLISH);
      }

      return List.of(new TextHudWidget.Line(LocalDateTime.now().format(this.format), Glass.TEXT));
   }
}
