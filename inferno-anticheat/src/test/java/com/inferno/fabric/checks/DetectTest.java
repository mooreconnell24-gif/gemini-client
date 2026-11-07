package com.inferno.fabric.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DetectTest {
   @Test
   void vanillaJumpReachesAboutOnePointTwoFive() {
      // Well-known vanilla figure: a normal jump peaks 1.2522 blocks up.
      assertEquals(1.2522, Detect.jumpApex(Detect.JUMP_IMPULSE), 0.0005);
      // The HighJump limit (1.35) sits above it with room for rounding.
      assertTrue(Detect.jumpApex(Detect.JUMP_IMPULSE) < 1.35);
      // A HighJump that launches with 0.6 instead of 0.42 goes well past it.
      assertTrue(Detect.jumpApex(0.6) > 1.35);
   }

   @Test
   void firstFallTickIsSmall() {
      assertEquals(-0.0784, Detect.fallSpeedAfter(1), 1.0E-9);
      // ReverseStep's -0.6 threshold is far beyond any first-tick fall.
      assertTrue(Detect.fallSpeedAfter(1) > -0.6);
   }

   @Test
   void sneakSpeedMatchesVanilla() {
      assertEquals(0.0648, Detect.sneakGroundSpeed(0.3, false), 0.0005);
      // Swift Sneak III raises the attribute to 0.75.
      assertEquals(0.1619, Detect.sneakGroundSpeed(0.75, false), 0.0005);
      // Clamped to walking speed.
      assertEquals(Detect.WALK_SPEED, Detect.sneakGroundSpeed(5.0, false), 1.0E-9);
   }

   @Test
   void coastingLosesSpeedOnNormalBlocks() {
      assertEquals(0.1092, Detect.coastSpeed(0.2, 0.6), 1.0E-9);
      assertTrue(Detect.coastSpeed(0.2, 0.98) > Detect.coastSpeed(0.2, 0.6));
   }

   @Test
   void statistics() {
      assertEquals(0.0, Detect.stdDev(List.of(50L, 50L, 50L)), 1.0E-12);
      assertEquals(2.0, Detect.mean(List.of(1, 2, 3)), 1.0E-12);
      assertEquals(1.0, Detect.stdDev(List.of(1, 2, 3)), 1.0E-12);
      assertEquals(0.0, Detect.stdDev(List.of(5)), 1.0E-12);
      assertEquals(0.0, Detect.mean(List.of()), 1.0E-12);
   }

   @Test
   void timerBalanceStaysFlatForAVanillaClient() {
      double b = 0.0;

      for (int i = 0; i < 1000; i++) {
         b = Detect.timerBalance(b, 50L, 50.0, -1000.0);
      }

      assertEquals(0.0, b, 1.0E-9);
   }

   @Test
   void timerBalanceGrowsForAFastClient() {
      // Timer 1.1x: a tick every ~45 ms gains 5 ms per tick, passing 250 ms after 50 ticks (2.5 s).
      double b = 0.0;

      for (int i = 0; i < 51; i++) {
         b = Detect.timerBalance(b, 45L, 50.0, -1000.0);
      }

      assertTrue(b > 250.0);
   }

   @Test
   void timerBalanceAbsorbsALagSpikeAndItsCatchUp() {
      // A 900 ms freeze followed by the client catching up 18 ticks in a burst nets out to ~0.
      double b = Detect.timerBalance(0.0, 950L, 50.0, -1000.0);

      for (int i = 0; i < 18; i++) {
         b = Detect.timerBalance(b, 0L, 50.0, -1000.0);
      }

      assertTrue(b <= 0.0 + 1.0E-9, "balance " + b);
      // The floor stops "saving up" more than a second of lag.
      assertEquals(-1000.0, Detect.timerBalance(0.0, 5000L, 50.0, -1000.0), 1.0E-9);
   }

   @Test
   void movePacketsPerTick() {
      assertFalse(Detect.tooManyMoves(0, false));
      assertFalse(Detect.tooManyMoves(1, false));
      assertTrue(Detect.tooManyMoves(2, false));
      assertFalse(Detect.tooManyMoves(2, true));
      assertTrue(Detect.tooManyMoves(3, true));
   }

   @Test
   void cheatBrands() {
      assertEquals("liquidbounce", Detect.cheatWord("LiquidBounce"));
      assertEquals("liquidbounce", Detect.cheatWord("liquidbounce-nextgen 0.30"));
      assertEquals("meteor", Detect.cheatWord("meteor-client"));
      assertEquals("wurst", Detect.cheatWord("Wurst Client"));
      assertEquals("baritone", Detect.cheatWord("baritone"));
      // Ordinary clients and mods must not match.
      assertNull(Detect.cheatWord("vanilla"));
      assertNull(Detect.cheatWord("fabric"));
      assertNull(Detect.cheatWord("forge"));
      assertNull(Detect.cheatWord("quilt"));
      assertNull(Detect.cheatWord("lunarclient:v2.18.1"));
      assertNull(Detect.cheatWord("feather"));
      assertNull(Detect.cheatWord("lambdynlights"));
      assertNull(Detect.cheatWord("sodium"));
      assertNull(Detect.cheatWord("impactful"));
      assertNull(Detect.cheatWord(null));
   }

   /** 16 click times: a fixed schedule plus uniform arrival jitter of +-jitter ms. */
   private static List<Long> botClicks(java.util.Random rnd, long every, double jitter) {
      List<Long> out = new java.util.ArrayList<>();

      for (int i = 0; i < 16; i++) {
         out.add(i * every + Math.round((rnd.nextDouble() * 2.0 - 1.0) * jitter));
      }

      return out;
   }

   /** 16 click times from a person whose intervals vary by {@code sd} ms (errors add up). */
   private static List<Long> personClicks(java.util.Random rnd, double mean, double sd) {
      List<Long> out = new java.util.ArrayList<>();
      long t = 0L;

      for (int i = 0; i < 16; i++) {
         out.add(t);
         t += Math.max(30L, Math.round(mean + rnd.nextGaussian() * sd));
      }

      return out;
   }

   @Test
   void scheduleDeviationSeparatesBotsFromPeople() {
      java.util.Random rnd = new java.util.Random(42);
      int botsCaught = 0;
      int peopleLow = 0;
      int peopleTwiceInARow = 0;
      int trials = 5000;
      boolean lastLow = false;

      for (int i = 0; i < trials; i++) {
         // A fixed-delay bot on a decent connection (+-5 ms) is always below the 6 ms limit.
         assertTrue(Detect.scheduleDeviation(botClicks(rnd, 100L, 5.0)) < 6.0);
         // A jittery link (+-10 ms) still leaves most windows below it.
         if (Detect.scheduleDeviation(botClicks(rnd, 100L, 10.0)) < 6.0) {
            botsCaught++;
         }

         // People: intervals around 150 ms varying by 20 ms.
         boolean low = Detect.scheduleDeviation(personClicks(rnd, 150.0, 20.0)) < 6.0;
         if (low) {
            peopleLow++;
            if (lastLow) {
               peopleTwiceInARow++;
            }
         }

         lastLow = low;
      }

      assertTrue(botsCaught > trials / 3, "jittery bots caught " + botsCaught);
      assertTrue(peopleLow <= trials / 1000, "people below the limit " + peopleLow);
      assertEquals(0, peopleTwiceInARow, "two steady windows in a row from a person");
      // A perfect metronome deviates by nothing, whatever its speed.
      assertEquals(0.0, Detect.scheduleDeviation(List.of(0L, 50L, 100L, 150L, 200L)), 1.0E-9);
   }

   @Test
   void breakRatio() {
      // Progress 0.05/tick = 20 ticks = 1000 ms.
      assertEquals(1.0, Detect.breakRatio(1000L, 0.05F), 1.0E-6);
      assertEquals(0.8, Detect.breakRatio(800L, 0.05F), 1.0E-6);
      assertEquals(Double.POSITIVE_INFINITY, Detect.breakRatio(100L, 0.0F), 0.0);
   }
}
