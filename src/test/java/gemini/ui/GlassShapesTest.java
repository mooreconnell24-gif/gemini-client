package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlassShapesTest {
   @Test
   void aDiscMaskIsSolidInsideEmptyOutsideAndSoftOnTheRim() {
      for (int radius : new int[] {4, 8, 12, 20, 40}) {
         assertEquals(1.0F, Glass.maskCoverage(radius, radius, radius, 0), "centre of r=" + radius);
         assertEquals(1.0F, Glass.maskCoverage(radius - 1, 1, radius, 0), 0.01F, "just inside the top of r=" + radius);
         assertEquals(0.0F, Glass.maskCoverage(0, 0, radius, 0), "the corner pixel is outside r=" + radius);
         boolean partial = false;
         double area = 0.0;
         for (int y = 0; y < radius * 2; y++) {
            for (int x = 0; x < radius * 2; x++) {
               float c = Glass.maskCoverage(x, y, radius, 0);
               assertTrue(c >= 0.0F && c <= 1.0F);
               partial |= c > 0.05F && c < 0.95F;
               area += c;
               // the four quadrants are mirror images, so one texture serves every corner
               assertEquals(c, Glass.maskCoverage(radius * 2 - 1 - x, y, radius, 0), 1.0E-5F);
               assertEquals(c, Glass.maskCoverage(x, radius * 2 - 1 - y, radius, 0), 1.0E-5F);
            }
         }

         assertTrue(partial, "antialiased rim at r=" + radius);
         assertEquals(Math.PI * radius * radius, area, Math.PI * radius * radius * 0.03, "covers a disc of r=" + radius);
      }
   }

   @Test
   void aRingMaskIsHollow() {
      int radius = 16;
      int stroke = 2;
      assertEquals(0.0F, Glass.maskCoverage(radius, radius, radius, stroke), "hollow centre");
      assertEquals(1.0F, Glass.maskCoverage(radius, 0, radius, stroke), 0.05F, "solid on the rim");
      assertEquals(1.0F, Glass.maskCoverage(radius, 1, radius, stroke), 0.05F);
      assertEquals(0.0F, Glass.maskCoverage(radius, 4, radius, stroke), "empty inside the stroke");
      double area = 0.0;
      for (int y = 0; y < radius * 2; y++) {
         for (int x = 0; x < radius * 2; x++) {
            area += Glass.maskCoverage(x, y, radius, stroke);
         }
      }

      double expected = Math.PI * (radius * radius - (radius - stroke) * (radius - stroke));
      assertEquals(expected, area, expected * 0.05);
   }

   @Test
   void aShadowFadesSmoothlyOutwards() {
      float blur = 10.0F;
      assertEquals(0.5F, Glass.shadowFalloff(0.0F, blur), 1.0E-5F, "half strength at the edge");
      assertTrue(Glass.shadowFalloff(-blur, blur) > 0.97F, "full strength well inside");
      assertTrue(Glass.shadowFalloff(Glass.shadowMargin((int)blur), blur) < 0.01F, "gone where the texture ends");
      float previous = 1.0F;
      for (float d = -15.0F; d <= 15.0F; d += 0.25F) {
         float a = Glass.shadowFalloff(d, blur);
         assertTrue(a <= previous + 1.0E-6F, "monotonic at " + d);
         assertTrue(previous - a < 0.03F, "no visible step at " + d);
         previous = a;
      }

      assertEquals(1.0F, Glass.shadowFalloff(-1.0F, 0.0F));
      assertEquals(0.0F, Glass.shadowFalloff(1.0F, 0.0F));
      assertEquals(15, Glass.shadowMargin(10));
   }

   @Test
   void aCriticallyDampedSpringSettlesWithoutOvershoot() {
      Glass.Spring spring = new Glass.Spring(0.0F);
      float max = 0.0F;
      for (int frame = 0; frame < 120; frame++) {
         max = Math.max(max, spring.step(1.0F, 1.0F / 60.0F, true));
      }

      assertTrue(max <= 1.0005F, "no overshoot: " + max);
      assertTrue(spring.settled(1.0F), "settled after two seconds: " + spring.value);
   }

   @Test
   void anUnderdampedSpringOvershootsAndStillSettles() {
      Glass.Spring spring = new Glass.Spring(0.0F, 170.0F, 0.5F);
      float max = 0.0F;
      for (int frame = 0; frame < 240; frame++) {
         max = Math.max(max, spring.step(1.0F, 1.0F / 60.0F, true));
      }

      assertTrue(max > 1.02F, "bounces: " + max);
      assertTrue(max < 1.4F, "but not wildly: " + max);
      assertTrue(spring.settled(1.0F));
   }

   @Test
   void aSpringGivesTheSameMotionAtAnyFrameRate() {
      Glass.Spring fast = new Glass.Spring(0.0F);
      Glass.Spring slow = new Glass.Spring(0.0F);
      for (int frame = 0; frame < 144 / 4; frame++) {
         fast.step(1.0F, 1.0F / 144.0F, true);
      }

      for (int frame = 0; frame < 20 / 4; frame++) {
         slow.step(1.0F, 1.0F / 20.0F, true);
      }

      // a quarter of a second in, both are in the same place (and a long frame does not blow up)
      assertEquals(fast.value, slow.value, 0.03F);
      Glass.Spring stalled = new Glass.Spring(0.0F, 400.0F, 1.0F);
      float value = stalled.step(1.0F, 5.0F, true);
      assertTrue(value >= 0.0F && value <= 1.001F, "stable after a hitch: " + value);
   }

   @Test
   void aSpringSnapsWhenAnimationsAreOff() {
      Glass.Spring spring = new Glass.Spring(0.0F);
      assertEquals(1.0F, spring.step(1.0F, 0.016F, false));
      assertTrue(spring.settled(1.0F));
      spring.velocity = 3.0F;
      spring.set(0.25F);
      assertEquals(0.25F, spring.value);
      assertEquals(0.0F, spring.velocity);
      assertFalse(spring.settled(1.0F));
   }

   @Test
   void theNewEasingCurvesRunFromZeroToOne() {
      assertEquals(0.0F, Glass.easeOutQuint(0.0F));
      assertEquals(1.0F, Glass.easeOutQuint(1.0F));
      assertEquals(0.0F, Glass.easeInOutCubic(0.0F));
      assertEquals(0.5F, Glass.easeInOutCubic(0.5F), 1.0E-5F);
      assertEquals(1.0F, Glass.easeInOutCubic(1.0F));
      assertEquals(0.0F, Glass.easeInCubic(0.0F));
      assertEquals(1.0F, Glass.easeInCubic(1.0F));
      assertEquals(1.0F, Glass.easeOutQuint(4.0F), "clamped");
      float previousOut = 0.0F;
      float previousInOut = 0.0F;
      for (float t = 0.0F; t <= 1.0F; t += 0.02F) {
         assertTrue(Glass.easeOutQuint(t) >= previousOut);
         assertTrue(Glass.easeInOutCubic(t) >= previousInOut - 1.0E-6F);
         assertTrue(Glass.easeOutQuint(t) >= t, "ease-out is ahead of linear");
         assertTrue(Glass.easeInCubic(t) <= t, "ease-in is behind linear");
         previousOut = Glass.easeOutQuint(t);
         previousInOut = Glass.easeInOutCubic(t);
      }
   }

   @Test
   void aHoverStateFollowsTheMouseAndAPress() {
      // no client is running, so animations count as on and the state eases
      Glass.Hover hover = new Glass.Hover();
      assertEquals(0.0F, hover.emphasis());
      hover.update(true, false, 0.016F);
      assertTrue(hover.hover > 0.0F && hover.hover < 1.0F, "eases in: " + hover.hover);
      for (int i = 0; i < 60; i++) {
         hover.update(true, false, 0.016F);
      }

      assertEquals(1.0F, hover.hover);
      assertEquals(0.0F, hover.press);
      assertEquals(0.8F, hover.emphasis(), 1.0E-4F);
      assertEquals(0xFF222222, hover.fill(0xFF111111, 0xFF222222));
      assertEquals(1.0F, hover.scale());
      for (int i = 0; i < 60; i++) {
         hover.update(true, true, 0.016F);
      }

      assertEquals(1.0F, hover.press);
      assertEquals(1.0F, hover.emphasis());
      assertEquals(0.97F, hover.scale(), 1.0E-5F);
      assertEquals(Glass.lerp(0xFF222222, 0xFF111111, 0.5F), hover.fill(0xFF111111, 0xFF222222), "pressed sits between rest and hover");
      for (int i = 0; i < 120; i++) {
         hover.update(false, false, 0.016F);
      }

      assertEquals(0.0F, hover.hover);
      assertEquals(0.0F, hover.press);
      // focus lights a control the same way hover does
      for (int i = 0; i < 60; i++) {
         hover.update(false, false, true, 0.016F);
      }

      assertEquals(1.0F, hover.focus);
      assertEquals(0.8F, hover.emphasis(), 1.0E-4F);
      assertEquals(Glass.text(), hover.label(true));
      assertEquals(Glass.textFaint(), hover.label(false));
      hover.reset();
      assertEquals(0.0F, hover.focus);
      assertEquals(0.0F, hover.emphasis());
   }
}
