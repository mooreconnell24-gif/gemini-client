package gemini.module.modules.hud;

/**
 * Reads the server's weather timers the way {@code ServerLevel.advanceWeatherCycle} uses them.
 *
 * <p>While the clear-weather timer runs (set by {@code /weather clear}), rain and thunder stay off. Otherwise the
 * rain and thunder timers each count down and flip their state at zero, after which a new random duration is
 * drawn. So each timer tells exactly when its state flips next and nothing beyond that; a thunderstorm needs both
 * to be on at once.
 */
public final class WeatherClock {
   /** No storm can be predicted from the timers. */
   public static final int NONE = -1;

   private WeatherClock() {
   }

   public static String current(boolean raining, boolean thundering) {
      if (raining && thundering) {
         return "Thunderstorm";
      } else {
         return raining ? "Rain" : "Clear";
      }
   }

   /**
    * Ticks until a thunderstorm starts (0 when one is on), or {@link #NONE} when the timers do not promise one:
    * the later of the two starts has to come before the earlier of the two stops. A state that is off now and
    * starts later is assumed to last until the other one starts too (rain lasts at least 12000 ticks and thunder
    * 3600 once they begin, while their delays are much longer).
    */
   public static int stormIn(int clearTime, boolean raining, int rainTime, boolean thundering, int thunderTime) {
      if (clearTime > 0) {
         return NONE;
      }

      int rainOn = raining ? 0 : rainTime;
      int thunderOn = thundering ? 0 : thunderTime;
      if (!raining && rainTime <= 0 || !thundering && thunderTime <= 0) {
         // A timer at zero draws a fresh random delay on the next tick, so its next start is unknown.
         return NONE;
      }

      int start = Math.max(rainOn, thunderOn);
      // A state that is on now ends at its timer (a timer at zero rolls a duration next tick: unknown, treat as open).
      int rainOff = raining && rainTime > 0 ? rainTime : Integer.MAX_VALUE;
      int thunderOff = thundering && thunderTime > 0 ? thunderTime : Integer.MAX_VALUE;
      return start < Math.min(rainOff, thunderOff) ? start : NONE;
   }

   /** Ticks until the current thunderstorm ends, or {@link #NONE} when none is on or its end is not known yet. */
   public static int stormEndsIn(int clearTime, boolean raining, int rainTime, boolean thundering, int thunderTime) {
      if (clearTime > 0 || !raining || !thundering) {
         return NONE;
      }

      int rainOff = rainTime > 0 ? rainTime : Integer.MAX_VALUE;
      int thunderOff = thunderTime > 0 ? thunderTime : Integer.MAX_VALUE;
      int end = Math.min(rainOff, thunderOff);
      return end == Integer.MAX_VALUE ? NONE : end;
   }

   /** Ticks until it starts or stops raining (whichever is next), or {@link #NONE} while forced clear or unknown. */
   public static int rainFlipIn(int clearTime, int rainTime) {
      return clearTime > 0 || rainTime <= 0 ? NONE : rainTime;
   }
}
