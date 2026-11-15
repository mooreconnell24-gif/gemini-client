package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class UiMathTest {
   private static final float EPS = 1.0E-4F;

   private static UiMath.AxisSnap snap(float pos, float size, float[] starts, float[] sizes, float grid, boolean equal) {
      return UiMath.snapAxis(pos, size, 400.0F, 2.0F, true, starts, sizes, starts.length, 6.0F, 4.0F, grid, equal);
   }

   @Test
   void farFromEverythingRoundsToGrid() {
      UiMath.AxisSnap s = snap(101.0F, 20.0F, new float[0], new float[0], 8.0F, false);
      assertEquals(104.0F, s.pos, EPS);
      assertEquals(UiMath.SNAP_GRID, s.kind);
      assertTrue(Float.isNaN(s.line));
      assertFalse(s.magnetic());
   }

   @Test
   void noGridLeavesPositionAlone() {
      UiMath.AxisSnap s = snap(101.3F, 20.0F, new float[0], new float[0], 0.0F, false);
      assertEquals(101.3F, s.pos, EPS);
      assertEquals(UiMath.SNAP_NONE, s.kind);
   }

   @Test
   void snapsToScreenEdgesWithMargin() {
      UiMath.AxisSnap left = snap(5.0F, 20.0F, new float[0], new float[0], 8.0F, false);
      assertEquals(2.0F, left.pos, EPS);
      assertEquals(UiMath.SNAP_EDGE, left.kind);
      UiMath.AxisSnap right = snap(375.0F, 20.0F, new float[0], new float[0], 8.0F, false);
      assertEquals(378.0F, right.pos, EPS);
      assertEquals(398.0F, right.line, EPS);
   }

   @Test
   void snapsToScreenCentre() {
      UiMath.AxisSnap s = snap(183.0F, 30.0F, new float[0], new float[0], 8.0F, false);
      assertEquals(185.0F, s.pos, EPS);
      assertEquals(200.0F, s.line, EPS);
      assertEquals(UiMath.SNAP_CENTER, s.kind);
   }

   @Test
   void alignsEdgesAndCentresOfOtherElements() {
      float[] starts = {100.0F};
      float[] sizes = {40.0F};
      UiMath.AxisSnap start = snap(98.0F, 20.0F, starts, sizes, 8.0F, false);
      assertEquals(100.0F, start.pos, EPS);
      assertEquals(UiMath.SNAP_ALIGN, start.kind);
      UiMath.AxisSnap end = snap(118.0F, 20.0F, starts, sizes, 8.0F, false);
      assertEquals(120.0F, end.pos, EPS);
      assertEquals(140.0F, end.line, EPS);
      UiMath.AxisSnap middle = snap(112.0F, 10.0F, starts, sizes, 8.0F, false);
      assertEquals(115.0F, middle.pos, EPS);
      assertEquals(120.0F, middle.line, EPS);
   }

   @Test
   void buttsUpWithGap() {
      UiMath.AxisSnap s = snap(146.0F, 20.0F, new float[]{100.0F}, new float[]{40.0F}, 8.0F, false);
      assertEquals(144.0F, s.pos, EPS);
      assertEquals(UiMath.SNAP_GAP, s.kind);
   }

   @Test
   void closestTargetWins() {
      // 99 is 1px from the other element's start and 3px from nothing else nearer
      UiMath.AxisSnap s = snap(99.0F, 20.0F, new float[]{100.0F, 95.0F}, new float[]{40.0F, 3.0F}, 8.0F, false);
      assertEquals(100.0F, s.pos, EPS);
   }

   @Test
   void equalSpacingContinuesARow() {
      // two elements 30px apart: 50..80 and 110..140; the next one wants to sit at 170
      float[] starts = {50.0F, 110.0F};
      float[] sizes = {30.0F, 30.0F};
      UiMath.AxisSnap s = snap(173.0F, 30.0F, starts, sizes, 8.0F, true);
      assertEquals(170.0F, s.pos, EPS);
      assertEquals(UiMath.SNAP_EQUAL, s.kind);
      UiMath.AxisSnap off = snap(173.0F, 30.0F, starts, sizes, 8.0F, false);
      assertEquals(UiMath.SNAP_GRID, off.kind);
   }

   @Test
   void screenTargetsCanBeTurnedOff() {
      UiMath.AxisSnap s = UiMath.snapAxis(4.0F, 20.0F, 400.0F, 2.0F, false, new float[0], new float[0], 0, 6.0F, 4.0F, 8.0F, false);
      assertEquals(8.0F, s.pos, EPS);
      assertEquals(UiMath.SNAP_GRID, s.kind);
   }

   @Test
   void sliderFollowsCursorNormally() {
      assertEquals(0.5, UiMath.sliderDrag(0.1, 0.0, 60.0, 10.0, 100.0, false), 1.0E-9);
      assertEquals(0.0, UiMath.sliderDrag(0.1, 0.0, -50.0, 10.0, 100.0, false), 1.0E-9);
      assertEquals(1.0, UiMath.sliderDrag(0.1, 0.0, 500.0, 10.0, 100.0, false), 1.0E-9);
   }

   @Test
   void preciseDragIsRelativeAndTenTimesSlower() {
      // moving the cursor 50px over a 100px track moves the knob 5% instead of 50%, wherever the cursor is
      assertEquals(0.35, UiMath.sliderDrag(0.3, 200.0, 250.0, 10.0, 100.0, true), 1.0E-9);
      assertEquals(0.25, UiMath.sliderDrag(0.3, 200.0, 150.0, 10.0, 100.0, true), 1.0E-9);
      assertEquals(1.0, UiMath.sliderDrag(0.99, 0.0, 100.0, 10.0, 100.0, true), 1.0E-9);
   }

   @Test
   void parsesPlainNumbers() {
      assertEquals(2.0, UiMath.parseNumber("2").getAsDouble(), 1.0E-9);
      assertEquals(-2.5, UiMath.parseNumber(" -2.5 ").getAsDouble(), 1.0E-9);
      assertEquals(0.5, UiMath.parseNumber(".5").getAsDouble(), 1.0E-9);
      assertEquals(3.5, UiMath.parseNumber("3,5").getAsDouble(), 1.0E-9);
      assertEquals(4.0, UiMath.parseNumber("+4").getAsDouble(), 1.0E-9);
      assertEquals(7.0, UiMath.parseNumber("7.").getAsDouble(), 1.0E-9);
   }

   @Test
   void rejectsAnythingElse() {
      for (String bad : new String[]{null, "", "  ", "-", ".", "abc", "1e3", "1.2.3", "2-1", "0x10", "NaN", "Infinity", "5f", "--1", "+-1"}) {
         OptionalDouble parsed = UiMath.parseNumber(bad);
         assertFalse(parsed.isPresent(), "should reject " + bad);
      }
   }

   @Test
   void glideTravelsTheRequestedDistanceRegardlessOfFrameRate() {
      float friction = 11.0F;
      float coarsePos = 0.0F;
      float coarseVel = 90.0F * friction;
      float finePos = 0.0F;
      float fineVel = coarseVel;
      for (int i = 0; i < 60; i++) {
         coarsePos = UiMath.glidePosition(coarsePos, coarseVel, friction, 1.0F / 30.0F);
         coarseVel = UiMath.glideVelocity(coarseVel, friction, 1.0F / 30.0F);
      }

      for (int i = 0; i < 240; i++) {
         finePos = UiMath.glidePosition(finePos, fineVel, friction, 1.0F / 120.0F);
         fineVel = UiMath.glideVelocity(fineVel, friction, 1.0F / 120.0F);
      }

      assertEquals(90.0F, coarsePos, 0.2F);
      assertEquals(coarsePos, finePos, 0.1F);
      assertEquals(0.0F, coarseVel, EPS);
   }
}
