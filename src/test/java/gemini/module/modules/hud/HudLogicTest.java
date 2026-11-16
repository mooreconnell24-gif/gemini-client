package gemini.module.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Session Stats' tally, Weather Forecast's timer reading and Mob Caps' cap formula. */
class HudLogicTest {
   @Test
   void firstReadingIsTheZeroPoint() {
      SessionTally tally = new SessionTally(2);
      tally.reset(0L);
      assertFalse(tally.primed());
      assertEquals(0L, tally.gained(0));
      tally.update(new long[]{500L, 7L});
      assertTrue(tally.primed());
      assertEquals(0L, tally.gained(0));
      tally.update(new long[]{530L, 9L});
      assertEquals(30L, tally.gained(0));
      assertEquals(2L, tally.gained(1));
   }

   @Test
   void counterResetMovesTheBaselineDown() {
      SessionTally tally = new SessionTally(1);
      tally.reset(0L);
      tally.update(new long[]{100L});
      tally.update(new long[]{120L});
      tally.update(new long[]{5L});
      assertEquals(0L, tally.gained(0), "a drop never shows as a negative gain");
      tally.update(new long[]{15L});
      assertEquals(10L, tally.gained(0));
   }

   @Test
   void resetStartsAgainAndRatesWaitAMinute() {
      SessionTally tally = new SessionTally(1);
      tally.reset(1_000L);
      tally.update(new long[]{10L});
      tally.update(new long[]{70L});
      assertTrue(Double.isNaN(tally.perHour(0, 30_000L)));
      assertEquals(60.0 * 30.0, tally.perHour(0, 1_000L + 120_000L), 1e-9);
      tally.reset(200_000L);
      assertFalse(tally.primed());
      tally.update(new long[]{70L});
      assertEquals(0L, tally.gained(0));
   }

   @Test
   void weatherNames() {
      assertEquals("Clear", WeatherClock.current(false, true));
      assertEquals("Rain", WeatherClock.current(true, false));
      assertEquals("Thunderstorm", WeatherClock.current(true, true));
   }

   @Test
   void forcedClearEndsInAStorm() {
      // ServerLevel holds both timers at 1 while the clear spell runs, so both flip on the tick after it ends.
      assertEquals(601, WeatherClock.rainFlipIn(600, 200));
      assertEquals(601, WeatherClock.stormIn(600, false, 200, false, 100));
      assertEquals(WeatherClock.NONE, WeatherClock.stormEndsIn(600, true, 200, true, 100));
   }

   @Test
   void stormTimeComesFromTheThunderTimerNotTheRainTimer() {
      // Thunder starts at 500 and lasts 3600..15600; rain starts at 1000, while thunder is surely still on.
      assertEquals(1000, WeatherClock.stormIn(0, false, 1000, false, 500));
      // Rain at 20000 is 19500 after thunder starts: the thunder spell is over by then, no storm.
      assertEquals(WeatherClock.NONE, WeatherClock.stormIn(0, false, 20000, false, 500));
      assertEquals(WeatherClock.NONE, WeatherClock.stormMaybeIn(0, false, 20000, false, 500));
      // 10000 after thunder starts: only if thunder lasts that long.
      assertEquals(WeatherClock.NONE, WeatherClock.stormIn(0, false, 10500, false, 500));
      assertEquals(10500, WeatherClock.stormMaybeIn(0, false, 10500, false, 500));
      // Rain first at 300, thunder at 20000: rain lasts 12000..24000, so possible.
      assertEquals(20000, WeatherClock.stormMaybeIn(0, false, 300, false, 20000));
      // Rain first at 300, thunder at 40000: rain is over long before.
      assertEquals(WeatherClock.NONE, WeatherClock.stormMaybeIn(0, false, 300, false, 40000));
      // A storm on now is neither "in" nor "maybe" ahead.
      assertEquals(WeatherClock.NONE, WeatherClock.stormMaybeIn(0, true, 300, true, 400));
   }

   @Test
   void stormNeedsBothStatesOnAtOnce() {
      // Rain starts at 300, thunder at 500: a storm at 500 unless the rain stops first (unknown, it is not on yet).
      assertEquals(500, WeatherClock.stormIn(0, false, 300, false, 500));
      // Raining now until 400, thunder from 500: the rain is gone before the thunder starts.
      assertEquals(WeatherClock.NONE, WeatherClock.stormIn(0, true, 400, false, 500));
      // Raining until 900, thunder from 500: storm at 500.
      assertEquals(500, WeatherClock.stormIn(0, true, 900, false, 500));
      // Thundering until 200 with no rain, rain from 100: storm at 100.
      assertEquals(100, WeatherClock.stormIn(0, false, 100, true, 200));
      // A timer at zero rolls a new random delay: nothing can be promised.
      assertEquals(WeatherClock.NONE, WeatherClock.stormIn(0, false, 0, false, 500));
   }

   @Test
   void stormEndsWithTheFirstStateToStop() {
      assertEquals(0, WeatherClock.stormIn(0, true, 400, true, 250));
      assertEquals(250, WeatherClock.stormEndsIn(0, true, 400, true, 250));
      assertEquals(WeatherClock.NONE, WeatherClock.stormEndsIn(0, true, 400, false, 250));
      assertEquals(WeatherClock.NONE, WeatherClock.stormEndsIn(0, true, 0, true, 0));
      assertEquals(120, WeatherClock.rainFlipIn(0, 120));
      assertEquals(WeatherClock.NONE, WeatherClock.rainFlipIn(0, 0));
   }

   @Test
   void mobCapScalesWithSpawnableChunks() {
      // One player alone: 17x17 = 289 chunks, so the cap is the per-chunk limit itself (70 monsters).
      assertEquals(70, MobCaps.globalCap(70, MobCaps.CHUNK_AREA));
      assertEquals(10, MobCaps.globalCap(10, 289));
      // Fewer spawnable chunks (render distance below 8) shrink the cap, rounding down like the spawner.
      assertEquals(35, MobCaps.globalCap(70, 145));
      assertEquals(0, MobCaps.globalCap(70, 0));
   }

   @Test
   void radarPutsWhatIsAheadAtTheTopForEveryHeading() {
      // facing south (yaw 0): +Z ahead, west (-X) to the right
      assertEquals(1.0, Radar.forward(0, 1, 0.0F), 1e-9);
      assertEquals(1.0, Radar.right(-1, 0, 0.0F), 1e-9);
      // facing west (yaw 90): -X ahead, north (-Z) to the right; the old maths drew this one mirrored
      assertEquals(1.0, Radar.forward(-1, 0, 90.0F), 1e-9);
      assertEquals(1.0, Radar.right(0, -1, 90.0F), 1e-9);
      // facing north (yaw 180) and east (yaw -90)
      assertEquals(1.0, Radar.forward(0, -1, 180.0F), 1e-9);
      assertEquals(1.0, Radar.forward(1, 0, -90.0F), 1e-9);
      assertEquals(1.0, Radar.right(0, 1, -90.0F), 1e-9);
   }
}
