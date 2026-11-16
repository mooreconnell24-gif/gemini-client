package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.util.AttributeBoost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

/**
 * A weather forecast that is never wrong: the integrated server already knows when the rain starts or stops and
 * when thunder is due, so once a second its weather timers are read and turned into countdowns by
 * {@link WeatherClock}.
 */
public final class WeatherForecast extends Ticked {
   private final BooleanSetting rain = this.addSetting(new BooleanSetting("Rain Timer", "Show when the rain starts or stops", true));
   private final BooleanSetting storm = this.addSetting(new BooleanSetting("Storm Timer", "Show when the next thunderstorm starts or the current one ends", true));
   private final BooleanSetting ticks = this.addSetting(new BooleanSetting("Show Ticks", "Show game ticks instead of minutes and seconds", false));
   private volatile Snapshot snapshot;
   private int poll;

   public WeatherForecast() {
      super("Weather Forecast", "Shows when the rain starts or stops and when the next thunderstorm comes.", "Top Left", 6, 196, 250);
   }

   private record Snapshot(boolean hasWeather, boolean cycle, int clear, boolean raining, int rainTime, boolean thundering, int thunderTime) {
   }

   @Override
   public void onEnable() {
      this.snapshot = null;
      this.poll = 0;
   }

   @Override
   public void onDisable() {
      this.snapshot = null;
   }

   @Override
   public void onWorldChange() {
      this.snapshot = null;
      this.poll = 0;
   }

   @Override
   public void onTick() {
      if (this.poll-- <= 0) {
         this.poll = 20;
         AttributeBoost.onServer(this::read);
      }
   }

   /** Runs on the server thread. */
   private void read(ServerPlayer player) {
      ServerLevel level = player.level();
      if (!level.canHaveWeather()) {
         this.snapshot = new Snapshot(false, false, 0, false, 0, false, 0);
         return;
      }

      WeatherData data = level.getWeatherData();
      boolean cycle = level.getGameRules().get(GameRules.ADVANCE_WEATHER);
      this.snapshot = new Snapshot(
         true, cycle, data.getClearWeatherTime(), data.isRaining(), data.getRainTime(), data.isThundering(), data.getThunderTime()
      );
   }

   private String time(int value) {
      return this.ticks.get() ? value + "t" : DayCycle.duration(value);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Snapshot s = this.snapshot;
      if (s == null) {
         return List.of();
      }

      if (!s.hasWeather()) {
         return List.of(new TextHudWidget.Line("No weather in this dimension", Glass.TEXT_FAINT));
      }

      List<TextHudWidget.Line> lines = new ArrayList<>(3);
      String now = WeatherClock.current(s.raining(), s.thundering());
      int nowColor = s.thundering() && s.raining() ? Glass.WARN : (s.raining() ? Glass.INFO : Glass.TEXT);
      lines.add(new TextHudWidget.Line("Weather: " + now + (s.cycle() ? "" : "  (cycle off)"), nowColor));
      if (!s.cycle()) {
         return lines;
      }

      if (s.clear() > 0) {
         lines.add(new TextHudWidget.Line("Kept clear for " + this.time(s.clear()), Glass.TEXT_DIM));
      }

      if (this.rain.get()) {
         int flip = WeatherClock.rainFlipIn(s.clear(), s.rainTime());
         if (flip != WeatherClock.NONE) {
            lines.add(new TextHudWidget.Line((s.raining() ? "Rain stops in " : "Rain in ") + this.time(flip), Glass.TEXT_DIM));
         }
      }

      if (this.storm.get()) {
         int ends = WeatherClock.stormEndsIn(s.clear(), s.raining(), s.rainTime(), s.thundering(), s.thunderTime());
         int starts = WeatherClock.stormIn(s.clear(), s.raining(), s.rainTime(), s.thundering(), s.thunderTime());
         int maybe = WeatherClock.stormMaybeIn(s.clear(), s.raining(), s.rainTime(), s.thundering(), s.thunderTime());
         if (ends != WeatherClock.NONE) {
            lines.add(new TextHudWidget.Line("Storm ends in " + this.time(ends), Glass.WARN));
         } else if (starts > 0) {
            lines.add(new TextHudWidget.Line("Storm in " + this.time(starts), Glass.WARN));
         } else if (maybe > 0) {
            lines.add(new TextHudWidget.Line("Storm possible in " + this.time(maybe), Glass.TEXT_DIM));
         }
         // no line when the timers rule a storm out: nothing to forecast
      }

      return lines;
   }
}
