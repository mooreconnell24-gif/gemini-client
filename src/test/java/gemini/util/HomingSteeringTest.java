package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HomingSteeringTest {
   /** A block world made of solid unit cells, with a ray-marching probe. */
   private static final class World implements HomingSteering.Solid, HomingSteering.Probe {
      private final Set<Long> cells = new HashSet<>();

      void fill(int x1, int y1, int z1, int x2, int y2, int z2) {
         for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
               for (int z = z1; z <= z2; z++) {
                  this.cells.add(key(x, y, z));
               }
            }
         }
      }

      void clearCells(int x1, int y1, int z1, int x2, int y2, int z2) {
         for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
               for (int z = z1; z <= z2; z++) {
                  this.cells.remove(key(x, y, z));
               }
            }
         }
      }

      private static long key(int x, int y, int z) {
         return ((long)(x + 1000) * 4000L + (y + 1000)) * 4000L + (z + 1000);
      }

      @Override
      public boolean solid(int x, int y, int z) {
         return this.cells.contains(key(x, y, z));
      }

      @Override
      public boolean clear(double x, double y, double z, double dx, double dy, double dz, double length) {
         for (double t = 0.0; t <= length; t += 0.05) {
            if (this.solid((int)Math.floor(x + dx * t), (int)Math.floor(y + dy * t), (int)Math.floor(z + dz * t))) {
               return false;
            }
         }

         return true;
      }
   }

   private static double length(double[] v) {
      return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
   }

   private static double angleDeg(double[] a, double[] b) {
      double dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (length(a) * length(b));
      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
   }

   @Test
   void clearPathFliesStraightAtTheTarget() {
      World world = new World();
      double[] v = HomingSteering.steer(0.5, 0.5, 0.5, 2.0, 0.0, 0.0, 20.5, 0.5, 0.5, 2.0, 30.0, 6.0, world, world);
      assertEquals(2.0, length(v), 1.0E-9, "keeps the set speed");
      assertTrue(angleDeg(v, new double[]{1, 0, 0}) < 0.01);
   }

   @Test
   void wallAheadDeflectsAroundIt() {
      World world = new World();
      world.fill(4, -3, -3, 4, 3, 3); // a 7x7 wall between the arrow and the target
      double[] dir = HomingSteering.chooseDirection(0.5, 0.5, 0.5, 1.0, 0.0, 0.0, 12.5, 0.5, 0.5, 6.0, world, null);
      assertTrue(angleDeg(dir, new double[]{1, 0, 0}) > 5.0, "turns away from the straight line");
      assertTrue(world.clear(0.5, 0.5, 0.5, dir[0], dir[1], dir[2], 6.0), "the chosen direction is clear");
      assertTrue(dir[0] > 0.0, "still makes progress toward the target");
   }

   @Test
   void flyingThroughAWallLeadsAroundItToTheTarget() {
      World world = new World();
      world.fill(6, -4, -4, 6, 4, 4);
      double x = 0.5;
      double y = 0.5;
      double z = 0.5;
      double[] v = {1.0, 0.0, 0.0};
      boolean reached = false;
      for (int tick = 0; tick < 80 && !reached; tick++) {
         v = HomingSteering.steer(x, y, z, v[0], v[1], v[2], 14.5, 0.5, 0.5, 0.8, 35.0, 4.0, world, world);
         assertTrue(world.clear(x, y, z, v[0] / length(v), v[1] / length(v), v[2] / length(v), length(v)), "never flies into the wall at tick " + tick);
         x += v[0];
         y += v[1];
         z += v[2];
         reached = Math.abs(x - 14.5) < 1.0 && Math.abs(y - 0.5) < 1.0 && Math.abs(z - 0.5) < 1.0;
      }

      assertTrue(reached, "reaches the target behind the wall");
   }

   @Test
   void respectsTheTurnRateLimit() {
      double[] v = HomingSteering.steer(0, 0, 0, 1.0, 0.0, 0.0, 0.0, 0.0, 10.0, 1.0, 10.0, 5.0, null, null);
      assertEquals(10.0, angleDeg(v, new double[]{1, 0, 0}), 1.0E-6);
      assertTrue(v[2] > 0.0, "turns toward the target side");
      double[] behind = HomingSteering.limitTurn(1, 0, 0, -1, 0, 0, 25.0);
      assertEquals(25.0, angleDeg(behind, new double[]{1, 0, 0}), 1.0E-6, "a target straight behind still turns by the limit");
   }

   @Test
   void neverReturnsAZeroVector() {
      World sealed = new World();
      sealed.fill(-3, -3, -3, 3, 3, 3);
      sealed.clearCells(0, 0, 0, 0, 0, 0);
      double[] boxed = HomingSteering.steer(0.5, 0.5, 0.5, 0, 0, 0, 10, 0.5, 0.5, 1.5, 20, 5, sealed, sealed);
      assertTrue(length(boxed) > 1.0, "sealed in");
      double[] onTarget = HomingSteering.steer(1, 1, 1, 0, 0, 0, 1, 1, 1, 1.5, 20, 5, null, null);
      assertTrue(length(onTarget) > 1.0, "already at the target with no velocity");
      double[] zeroSpeed = HomingSteering.steer(0, 0, 0, 1, 0, 0, 5, 0, 0, 0.0, 20, 5, null, null);
      assertTrue(length(zeroSpeed) > 0.0, "zero speed setting");
   }

   @Test
   void gridSearchFindsTheWayOutOfATunnel() {
      World world = new World();
      // A solid block with an L-shaped tunnel: +X for 4 cells, then +Z for 6.
      world.fill(-2, -2, -2, 8, 2, 9);
      world.clearCells(0, 0, 0, 4, 0, 0);
      world.clearCells(4, 0, 0, 4, 0, 7);
      List<int[]> path = HomingSteering.aStar(0, 0, 0, 4, 0, 7, world);
      assertNotNull(path);
      double[] dir = HomingSteering.chooseDirection(0.5, 0.5, 0.5, 0, 0, 1, 4.5, 0.5, 7.5, 8.0, world, world);
      assertTrue(dir[0] > 0.9, "heads down the tunnel first, not into the wall toward the target");
   }

   @Test
   void segmentBoxTest() {
      assertTrue(HomingSteering.segmentHitsBox(0, 0, 0, 1, 0, 0, 5, 2, -0.5, -0.5, 3, 0.5, 0.5));
      assertTrue(!HomingSteering.segmentHitsBox(0, 0, 0, 1, 0, 0, 1.5, 2, -0.5, -0.5, 3, 0.5, 0.5), "too short");
      assertTrue(!HomingSteering.segmentHitsBox(0, 2, 0, 1, 0, 0, 5, 2, -0.5, -0.5, 3, 0.5, 0.5), "passes above");
   }
}
