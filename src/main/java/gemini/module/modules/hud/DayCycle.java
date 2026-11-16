package gemini.module.modules.hud;

/**
 * The overworld day cycle worked out from the clock time alone, so it can be unit tested without a world.
 *
 * <p>The numbers come from the vanilla data pack: {@code timeline/moon.json} steps the moon through eight phases,
 * one per 24000-tick day starting at the full moon, and sets the surface slime spawn chance per phase;
 * {@code timeline/day.json} switches {@code monsters_burn} off from tick 12542 to 23460 of each day, which is
 * also the stretch in which a bed lets you sleep on a clear night.
 */
public final class DayCycle {
   public static final long DAY = 24000L;
   /** First tick of the night: monsters stop burning and beds can be used. */
   public static final int NIGHT_START = 12542;
   /** First tick of the morning: monsters burn again. */
   public static final int NIGHT_END = 23460;
   private static final String[] PHASES = {
      "Full Moon", "Waning Gibbous", "Third Quarter", "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"
   };
   private static final double[] SLIME_CHANCE = {0.5, 0.375, 0.25, 0.125, 0.0, 0.125, 0.25, 0.375};

   private DayCycle() {
   }

   /** Whole days since the world started (the number the debug screen shows). */
   public static long day(long clockTicks) {
      return Math.floorDiv(clockTicks, DAY);
   }

   /** Ticks into the current day, 0..23999. */
   public static int timeOfDay(long clockTicks) {
      return (int)Math.floorMod(clockTicks, DAY);
   }

   /** Moon phase index, 0 (full moon) to 7 (waxing gibbous). */
   public static int moonPhase(long clockTicks) {
      return (int)Math.floorMod(day(clockTicks), 8L);
   }

   public static String phaseName(int phase) {
      return PHASES[Math.floorMod(phase, PHASES.length)];
   }

   /** The chance a slime may spawn in a swamp tonight, before the light check (0 at new moon, 0.5 at full moon). */
   public static double slimeChance(int phase) {
      return SLIME_CHANCE[Math.floorMod(phase, SLIME_CHANCE.length)];
   }

   public static boolean isNight(int timeOfDay) {
      return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
   }

   /** Ticks until night falls (during the day) or until morning (during the night). */
   public static int ticksUntilChange(int timeOfDay) {
      int t = (int)Math.floorMod(timeOfDay, DAY);
      if (isNight(t)) {
         return NIGHT_END - t;
      } else {
         return t < NIGHT_START ? NIGHT_START - t : (int)(DAY - t) + NIGHT_START;
      }
   }

   /** The in-game wall clock, "HH:MM", with tick 0 at 06:00 like the vanilla clock item. */
   public static String clock(int timeOfDay) {
      int t = (int)Math.floorMod(timeOfDay, DAY);
      int hour = (t / 1000 + 6) % 24;
      int minute = t % 1000 * 60 / 1000;
      return (hour < 10 ? "0" : "") + hour + (minute < 10 ? ":0" : ":") + minute;
   }

   /** A tick count as real time at the normal 20 ticks a second, "m:ss". */
   public static String duration(int ticks) {
      int seconds = Math.max(0, ticks) / 20;
      int rest = seconds % 60;
      return seconds / 60 + (rest < 10 ? ":0" : ":") + rest;
   }
}
