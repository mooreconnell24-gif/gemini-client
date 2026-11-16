package gemini.module.modules.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class StrongholdMathTest {
   @Test
   void yawMatchesMinecraftConvention() {
      assertEquals(0.0, StrongholdMath.yaw(0.0, 1.0), 1.0E-9);
      assertEquals(90.0, StrongholdMath.yaw(-1.0, 0.0), 1.0E-9);
      assertEquals(-90.0, StrongholdMath.yaw(1.0, 0.0), 1.0E-9);
      assertEquals(180.0, Math.abs(StrongholdMath.yaw(0.0, -1.0)), 1.0E-9);
   }

   @Test
   void twoThrowsCrossAtTheStronghold() {
      double sx = 1200.0;
      double sz = -840.0;
      // Two throws from different spots, each aimed at the stronghold.
      double[] hit = StrongholdMath.intersect(0.0, 0.0, sx, sz, 300.0, 200.0, sx - 300.0, sz - 200.0);
      assertNotNull(hit);
      assertArrayEquals(new double[]{sx, sz}, hit, 1.0E-6);
   }

   @Test
   void directionLengthDoesNotMatter() {
      double[] a = StrongholdMath.intersect(0.0, 0.0, 1.0, 1.0, 10.0, 0.0, -1.0, 1.0);
      double[] b = StrongholdMath.intersect(0.0, 0.0, 0.2, 0.2, 10.0, 0.0, -7.0, 7.0);
      assertArrayEquals(new double[]{5.0, 5.0}, a, 1.0E-9);
      assertArrayEquals(a, b, 1.0E-9);
   }

   @Test
   void nearlyParallelThrowsAreRejected() {
      assertNull(StrongholdMath.intersect(0.0, 0.0, 0.0, 1.0, 5.0, 0.0, 0.0, 1.0));
      // one degree apart: below the minimum angle
      double angle = Math.toRadians(1.0);
      assertNull(StrongholdMath.intersect(0.0, 0.0, 0.0, 1.0, 5.0, 0.0, -Math.sin(angle), Math.cos(angle)));
   }

   @Test
   void crossingBehindAThrowerIsRejected() {
      // The lines cross at (5, -5): ahead of the first thrower but behind the second.
      assertNull(StrongholdMath.intersect(0.0, 0.0, 1.0, -1.0, 10.0, 0.0, 1.0, 1.0));
      // A zero direction has no line.
      assertNull(StrongholdMath.intersect(0.0, 0.0, 0.0, 0.0, 10.0, 0.0, -1.0, 1.0));
   }
}
