package gemini.module.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.ui.HudStyle;
import gemini.ui.Theme;
import org.junit.jupiter.api.Test;

/** The number formats, the cooldown clock and the small geometry helpers behind the HUD widgets. */
class HudVisualLogicTest {
   @Test
   void groupedCountsUseCommas() {
      assertEquals("0", HudFormat.grouped(0L));
      assertEquals("999", HudFormat.grouped(999L));
      assertEquals("1,000", HudFormat.grouped(1000L));
      assertEquals("12,345", HudFormat.grouped(12345L));
      assertEquals("1,234,567", HudFormat.grouped(1234567L));
      assertEquals("-4,200", HudFormat.grouped(-4200L));
   }

   @Test
   void shortTimesKeepAFixedShape() {
      assertEquals("0.0", HudFormat.shortTime(0.0));
      assertEquals("0.0", HudFormat.shortTime(-3.0));
      assertEquals("3.4", HudFormat.shortTime(3.44));
      assertEquals("9.9", HudFormat.shortTime(9.94));
      assertEquals("10", HudFormat.shortTime(9.96));
      assertEquals("42", HudFormat.shortTime(42.2));
      assertEquals("1:00", HudFormat.shortTime(59.6));
      assertEquals("2:05", HudFormat.shortTime(125.0));
      assertTrue(HudFormat.shortTimeInSeconds(42.0));
      assertFalse(HudFormat.shortTimeInSeconds(125.0));
   }

   @Test
   void clocksPadMinutesAndSeconds() {
      assertEquals("0:00", HudFormat.clock(0L));
      assertEquals("0:09", HudFormat.clock(9L));
      assertEquals("12:34", HudFormat.clock(754L));
      assertEquals("1:02:03", HudFormat.clock(3723L));
   }

   @Test
   void remainingIsMeasuredAgainstTheLongestSeen() {
      assertEquals(0.5F, HudFormat.remaining(100, 200), 1.0E-6F);
      assertEquals(1.0F, HudFormat.remaining(300, 200), 1.0E-6F);
      assertEquals(1.0F, HudFormat.remaining(-1, 200), 1.0E-6F);
      assertEquals(1.0F, HudFormat.remaining(50, 0), 1.0E-6F);
   }

   @Test
   void cooldownClockReadsTheRateOfTheFraction() {
      CooldownClock clock = new CooldownClock();
      // A 2-second cooldown: the fraction falls by 0.5 a second.
      assertTrue(Double.isNaN(clock.sample(1.0, 0L)));
      assertTrue(Double.isNaN(clock.sample(0.99, 20L)), "too small a drop to trust");
      assertEquals(1.5, clock.sample(0.75, 500L), 1.0E-9);
      assertEquals(0.5, clock.sample(0.25, 1500L), 1.0E-9);
      // A new cooldown (the fraction jumps back up) starts measuring again.
      assertTrue(Double.isNaN(clock.sample(1.0, 2000L)));
      assertEquals(4.0, clock.sample(0.8, 3000L), 1.0E-9);
      clock.reset();
      assertTrue(Double.isNaN(clock.sample(0.5, 4000L)));
   }

   @Test
   void healthColourSlidesFromGreenThroughAmberToRed() {
      Theme.Palette theme = Theme.current();
      assertEquals(theme.ok | 0xFF000000, HudStyle.health(1.0F));
      assertEquals(theme.warn | 0xFF000000, HudStyle.health(0.5F));
      assertEquals(theme.error | 0xFF000000, HudStyle.health(0.0F));
      assertEquals(theme.error | 0xFF000000, HudStyle.health(-2.0F));
      assertEquals(theme.ok | 0xFF000000, HudStyle.health(3.0F));
   }

   @Test
   void compassHeadingIsZeroAtNorth() {
      // Minecraft yaw: 0 south, 90 west, 180 north, -90 east.
      assertEquals(0, CompassBar.heading(180.0F));
      assertEquals(180, CompassBar.heading(0.0F));
      assertEquals(270, CompassBar.heading(90.0F));
      assertEquals(90, CompassBar.heading(-90.0F));
      assertEquals(90, CompassBar.heading(270.0F));
      assertEquals(0, CompassBar.heading(-180.4F));
   }

   @Test
   void radarKeepsDotsInsideItsShape() {
      assertTrue(Radar.inside(0, 0, 30, true));
      assertTrue(Radar.inside(30, 0, 30, true));
      assertFalse(Radar.inside(22, 22, 30, true), "a corner is outside the circle");
      assertTrue(Radar.inside(22, 22, 30, false), "but inside the square");
      assertFalse(Radar.inside(31, 0, 30, false));
   }
}
