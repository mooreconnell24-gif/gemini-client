package gemini.module.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DayCycleTest {
   @Test
   void dayAndTimeOfDay() {
      assertEquals(0L, DayCycle.day(0L));
      assertEquals(2L, DayCycle.day(48_500L));
      assertEquals(500, DayCycle.timeOfDay(48_500L));
      assertEquals(-1L, DayCycle.day(-1L), "negative clock times still land on whole days");
      assertEquals(23_999, DayCycle.timeOfDay(-1L));
   }

   @Test
   void moonStartsFullAndCyclesEveryEightDays() {
      assertEquals(0, DayCycle.moonPhase(0L));
      assertEquals("Full Moon", DayCycle.phaseName(DayCycle.moonPhase(10L)));
      assertEquals("New Moon", DayCycle.phaseName(DayCycle.moonPhase(4L * DayCycle.DAY + 100L)));
      assertEquals(7, DayCycle.moonPhase(7L * DayCycle.DAY));
      assertEquals(0, DayCycle.moonPhase(8L * DayCycle.DAY));
   }

   @Test
   void slimeChanceFollowsMoonBrightness() {
      assertEquals(0.5, DayCycle.slimeChance(0));
      assertEquals(0.0, DayCycle.slimeChance(4));
      assertEquals(DayCycle.slimeChance(1), DayCycle.slimeChance(7));
   }

   @Test
   void nightWindowMatchesTheMonsterBurnTrack() {
      assertFalse(DayCycle.isNight(12_541));
      assertTrue(DayCycle.isNight(12_542));
      assertTrue(DayCycle.isNight(23_459));
      assertFalse(DayCycle.isNight(23_460));
   }

   @Test
   void countdownToTheNextChange() {
      assertEquals(12_542, DayCycle.ticksUntilChange(0));
      assertEquals(1, DayCycle.ticksUntilChange(12_541));
      assertEquals(23_460 - 13_000, DayCycle.ticksUntilChange(13_000));
      // After dawn the next change is tomorrow's dusk.
      assertEquals(24_000 - 23_500 + 12_542, DayCycle.ticksUntilChange(23_500));
   }

   @Test
   void clockAndDurationText() {
      assertEquals("06:00", DayCycle.clock(0));
      assertEquals("12:00", DayCycle.clock(6_000));
      assertEquals("00:00", DayCycle.clock(18_000));
      assertEquals("05:59", DayCycle.clock(23_999));
      assertEquals("0:00", DayCycle.duration(0));
      assertEquals("1:05", DayCycle.duration(1_300));
      assertEquals("0:00", DayCycle.duration(-20));
   }
}
