package gemini.module.modules.hud;

/**
 * Reads the server's weather timers the way {@code ServerLevel.advanceWeatherCycle} uses them.
 *
 * <p>While the clear-weather timer runs (set by {@code /weather clear}), rain and thunder stay off and both timers
 * are held at one tick, so both switch on right after it. Otherwise the
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

   /** Shortest rain and thunder spells ({@code ServerLevel.RAIN_DURATION} 12000..24000, {@code THUNDER_DURATION} 3600..15600). */
   static final int RAIN_MIN = 12000;
   static final int RAIN_MAX = 24000;
   static final int THUNDER_MIN = 3600;
   static final int THUNDER_MAX = 15600;

   /**
    * Ticks until a thunderstorm certainly starts (0 when one is on), or {@link #NONE} when the timers do not promise
    * one. See {@link #storm} for how it is worked out.
    */
   public static int stormIn(int clearTime, boolean raining, int rainTime, boolean thundering, int thunderTime) {
      long storm = storm(clearTime, raining, rainTime, thundering, thunderTime);
      return storm >= 0 && (storm & 1L) == 0L ? (int)(storm >> 1) : NONE;
   }

   /**
    * Ticks until a thunderstorm may start: the start is exact, but whether the state that begins first is still on
    * then depends on a duration the server has not drawn yet. {@link #NONE} when a storm is certain or impossible.
    */
   public static int stormMaybeIn(int clearTime, boolean raining, int rainTime, boolean thundering, int thunderTime) {
      long storm = storm(clearTime, raining, rainTime, thundering, thunderTime);
      return storm >= 0 && (storm & 1L) == 1L ? (int)(storm >> 1) : NONE;
   }

   /**
    * The next storm from the two timers, as {@code ticks << 1 | maybe}, or -1. A storm needs rain and thunder on at
    * once, so it starts when the later of the two starts, if the earlier one is still on by then. The earlier one's
    * end is exact when it is on now (its timer); when it only starts later, its length is drawn at that moment from
    * the ranges above, so the storm is certain when the gap is shorter than the shortest spell, impossible when it
    * is longer than the longest, and possible in between. A timer at zero draws a new random value next tick, so
    * nothing is known. While {@code /weather clear} runs both timers sit at one tick, so rain and thunder both start
    * the tick after it ends.
    */
   static long storm(int clearTime, boolean raining, int rainTime, boolean thundering, int thunderTime) {
      if (clearTime > 0) {
         return (long)(clearTime + 1) << 1;
      }

      if (raining && thundering) {
         return 0L;
      }

      if (!raining && rainTime <= 0 || !thundering && thunderTime <= 0) {
         return -1L;
      }

      int rainOn = raining ? 0 : rainTime;
      int thunderOn = thundering ? 0 : thunderTime;
      boolean rainFirst = rainOn <= thunderOn;
      int start = Math.max(rainOn, thunderOn);
      int firstStart = Math.min(rainOn, thunderOn);
      int gap = start - firstStart;
      boolean firstOnNow = rainFirst ? raining : thundering;
      if (firstOnNow) {
         int firstTimer = rainFirst ? rainTime : thunderTime;
         if (firstTimer <= 0) {
            // its length is drawn next tick: it lasts at least the shortest spell
            return gap < (rainFirst ? RAIN_MIN : THUNDER_MIN) ? (long)start << 1 : -1L;
         }

         return start < firstTimer ? (long)start << 1 : -1L;
      }

      int min = rainFirst ? RAIN_MIN : THUNDER_MIN;
      int max = rainFirst ? RAIN_MAX : THUNDER_MAX;
      if (gap < min) {
         return (long)start << 1;
      }

      return gap < max ? (long)start << 1 | 1L : -1L;
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

   /**
    * Ticks until it starts or stops raining (whichever is next), or {@link #NONE} when unknown. After a forced clear
    * spell the rain starts the tick after it ends.
    */
   public static int rainFlipIn(int clearTime, int rainTime) {
      if (clearTime > 0) {
         return clearTime + 1;
      }

      return rainTime <= 0 ? NONE : rainTime;
   }
}
