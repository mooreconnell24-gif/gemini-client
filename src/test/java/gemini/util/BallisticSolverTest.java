package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallisticSolverTest {
   private static final double G = BallisticSolver.ARROW_GRAVITY;
   private static final double D = BallisticSolver.ARROW_DRAG;

   /** Fires the solution through the tick physics and returns how far from the (moving) target it passes. */
   private static double miss(BallisticSolver.Solution s, double speed, double px, double py, double pz, double vx, double vy, double vz, double wx, double wy, double wz) {
      double[] at = new double[3];
      BallisticSolver.positionAt(s.dirX * speed + wx, s.dirY * speed + wy, s.dirZ * speed + wz, G, D, s.ticks, at);
      double tx = px + vx * s.ticks;
      double ty = py + vy * s.ticks;
      double tz = pz + vz * s.ticks;
      return Math.sqrt((at[0] - tx) * (at[0] - tx) + (at[1] - ty) * (at[1] - ty) + (at[2] - tz) * (at[2] - tz));
   }

   private static BallisticSolver.Solution solve(double px, double py, double pz, double vx, double vy, double vz, double speed, int arc) {
      BallisticSolver.Solution s = new BallisticSolver.Solution();
      assertTrue(BallisticSolver.solve(px, py, pz, vx, vy, vz, 0, 0, 0, speed, G, D, arc, 300, s), "expected a solution");
      return s;
   }

   @Test
   void hitsStaticTargetsAtSeveralDistancesAndHeights() {
      double[][] targets = {{10, 0, 0}, {0, 0, 25}, {30, 5, 30}, {-40, -10, 5}, {60, 0, 0}, {15, 12, -8}};
      for (double[] t : targets) {
         BallisticSolver.Solution s = solve(t[0], t[1], t[2], 0, 0, 0, 3.0, BallisticSolver.LOW);
         assertTrue(miss(s, 3.0, t[0], t[1], t[2], 0, 0, 0, 0, 0, 0) < 0.1, "miss too large for " + t[0] + "," + t[1] + "," + t[2]);
      }
   }

   @Test
   void tridentAndWeakBowAlsoHit() {
      BallisticSolver.Solution trident = new BallisticSolver.Solution();
      assertTrue(BallisticSolver.solve(20, 2, 0, 0, 0, 0, 0, 0, 0, BallisticSolver.TRIDENT_SPEED, G, D, BallisticSolver.LOW, 300, trident));
      assertTrue(miss(trident, BallisticSolver.TRIDENT_SPEED, 20, 2, 0, 0, 0, 0, 0, 0, 0) < 0.1);
      double weak = BallisticSolver.bowPower(8) * BallisticSolver.BOW_SPEED;
      BallisticSolver.Solution s = solve(12, 0, 3, 0, 0, 0, weak, BallisticSolver.LOW);
      assertTrue(miss(s, weak, 12, 0, 3, 0, 0, 0, 0, 0, 0) < 0.1);
   }

   @Test
   void leadsAMovingTarget() {
      double vx = 0.0;
      double vz = 0.25; // a sprinting target crossing the line of fire
      BallisticSolver.Solution s = solve(30, 0, 0, vx, 0, vz, 3.0, BallisticSolver.LOW);
      assertTrue(miss(s, 3.0, 30, 0, 0, vx, 0, vz, 0, 0, 0) < 0.1);
      assertTrue(s.dirZ > 0.01, "aims ahead of the target");
      assertTrue(s.hitZ > 1.0, "predicted impact is where the target will be");
   }

   @Test
   void accountsForTheShootersOwnMotion() {
      BallisticSolver.Solution s = new BallisticSolver.Solution();
      assertTrue(BallisticSolver.solve(25, 0, 0, 0, 0, 0, 0, 0, 0.3, 3.0, G, D, BallisticSolver.LOW, 300, s));
      assertTrue(miss(s, 3.0, 25, 0, 0, 0, 0, 0, 0, 0, 0.3) < 0.1);
      assertTrue(s.dirZ < 0.0, "aims against the inherited sideways motion");
   }

   @Test
   void reportsNoSolutionBeyondRange() {
      BallisticSolver.Solution s = new BallisticSolver.Solution();
      assertFalse(BallisticSolver.solve(400, 0, 0, 0, 0, 0, 0, 0, 0, 3.0, G, D, BallisticSolver.LOW, 300, s));
      assertFalse(BallisticSolver.solve(0, 300, 0, 0, 0, 0, 0, 0, 0, 3.0, G, D, BallisticSolver.LOW, 300, s), "straight up beyond the apex");
   }

   @Test
   void highArcFliesLongerAndSteeperThanLowArc() {
      BallisticSolver.Solution low = solve(40, 0, 0, 0, 0, 0, 3.0, BallisticSolver.LOW);
      BallisticSolver.Solution high = solve(40, 0, 0, 0, 0, 0, 3.0, BallisticSolver.HIGH);
      assertTrue(high.ticks > low.ticks);
      assertTrue(high.pitch < low.pitch, "the high arc points further up (more negative pitch)");
      assertTrue(miss(high, 3.0, 40, 0, 0, 0, 0, 0, 0, 0, 0) < 0.1);
      assertTrue(low.pitch < 0.0, "even the low arc points a little up on level ground");
   }

   @Test
   void simulateMatchesClosedForm() {
      double[] points = new double[3 * 30];
      int count = BallisticSolver.simulate(0, 0, 0, 2.0, 0.5, 0.0, G, D, 30, points);
      assertEquals(30, count);
      double[] at = new double[3];
      BallisticSolver.positionAt(2.0, 0.5, 0.0, G, D, 20.0, at);
      assertEquals(points[60], at[0], 1.0E-9);
      assertEquals(points[61], at[1], 1.0E-9);
      double s = BallisticSolver.s(20, D);
      double g = BallisticSolver.gTerm(20, D);
      assertEquals(2.0 * s, at[0], 1.0E-9);
      assertEquals(0.5 * s - G * g, at[1], 1.0E-9);
   }

   @Test
   void bowPowerMatchesVanilla() {
      assertEquals(1.0, BallisticSolver.bowPower(20), 1.0E-9);
      assertEquals(1.0, BallisticSolver.bowPower(40), 1.0E-9);
      assertEquals((0.25 + 1.0) / 3.0, BallisticSolver.bowPower(10), 1.0E-9);
   }

   @Test
   void yawAndPitchFollowMinecraftConventions() {
      assertEquals(0.0F, BallisticSolver.yawOf(0, 1), 1.0E-4F); // +Z is yaw 0
      assertEquals(-90.0F, BallisticSolver.yawOf(1, 0), 1.0E-4F); // +X is yaw -90
      assertEquals(-45.0F, BallisticSolver.pitchOf(1, 1, 0), 1.0E-4F); // up is negative pitch
   }
}
