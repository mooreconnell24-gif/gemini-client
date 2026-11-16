package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetPriorityTest {
   private static double score(String mode, double distanceSq, double health, double armor, double angle) {
      return TargetPriority.score(mode, TargetPriority.PREFER_ANY, distanceSq, health, armor, angle, false, 0.0);
   }

   @Test
   void closestPrefersTheNearerCandidate() {
      assertTrue(score(TargetPriority.CLOSEST, 4.0, 20, 0, 0) < score(TargetPriority.CLOSEST, 9.0, 1, 0, 0));
   }

   @Test
   void healthModesOrderByHealthAndBreakTiesByDistance() {
      assertTrue(score(TargetPriority.LOWEST_HEALTH, 100.0, 4, 0, 0) < score(TargetPriority.LOWEST_HEALTH, 1.0, 10, 0, 0));
      assertTrue(score(TargetPriority.HIGHEST_HEALTH, 100.0, 10, 0, 0) < score(TargetPriority.HIGHEST_HEALTH, 1.0, 4, 0, 0));
      assertTrue(score(TargetPriority.LOWEST_HEALTH, 1.0, 10, 0, 0) < score(TargetPriority.LOWEST_HEALTH, 100.0, 10, 0, 0), "tie goes to the closer one");
   }

   @Test
   void crosshairAndArmorModes() {
      assertTrue(score(TargetPriority.CROSSHAIR, 400.0, 20, 0, 3) < score(TargetPriority.CROSSHAIR, 1.0, 20, 0, 40));
      assertTrue(score(TargetPriority.MOST_ARMOR, 400.0, 20, 20, 0) < score(TargetPriority.MOST_ARMOR, 1.0, 20, 2, 0));
      assertTrue(score(TargetPriority.LEAST_ARMOR, 400.0, 20, 2, 0) < score(TargetPriority.LEAST_ARMOR, 1.0, 20, 20, 0));
   }

   @Test
   void preferPutsTheOtherKindLastWhateverItsScore() {
      double farPlayer = TargetPriority.score(TargetPriority.CLOSEST, TargetPriority.PREFER_PLAYERS, 3600.0, 20, 0, 0, true, 0);
      double nearMob = TargetPriority.score(TargetPriority.CLOSEST, TargetPriority.PREFER_PLAYERS, 1.0, 20, 0, 0, false, 0);
      assertTrue(farPlayer < nearMob);
      double farMob = TargetPriority.score(TargetPriority.CLOSEST, TargetPriority.PREFER_MOBS, 3600.0, 20, 0, 0, false, 0);
      double nearPlayer = TargetPriority.score(TargetPriority.CLOSEST, TargetPriority.PREFER_MOBS, 1.0, 20, 0, 0, true, 0);
      assertTrue(farMob < nearPlayer);
   }

   @Test
   void mostDangerousWeighsAttackersAboveBystanders() {
      double attacker = TargetPriority.threat(true, true, 6.0, 8.0);
      double bystander = TargetPriority.threat(false, false, 0.0, 2.0);
      assertTrue(attacker > bystander);
      assertTrue(TargetPriority.threat(false, true, 0.0, 2.0) > TargetPriority.threat(false, true, 0.0, 20.0), "closer is more dangerous");
      double a = TargetPriority.score(TargetPriority.MOST_DANGEROUS, TargetPriority.PREFER_ANY, 64.0, 20, 0, 0, false, attacker);
      double b = TargetPriority.score(TargetPriority.MOST_DANGEROUS, TargetPriority.PREFER_ANY, 4.0, 20, 0, 0, false, bystander);
      assertTrue(a < b);
   }

   @Test
   void angleBetweenDirections() {
      assertEquals(0.0, TargetPriority.angleTo(1, 0, 0, 5, 0, 0), 1.0E-9);
      assertEquals(90.0, TargetPriority.angleTo(1, 0, 0, 0, 0, 3), 1.0E-9);
      assertEquals(180.0, TargetPriority.angleTo(1, 0, 0, -2, 0, 0), 1.0E-9);
      assertEquals(0.0, TargetPriority.angleTo(0, 0, 0, 1, 0, 0), 1.0E-9, "a zero vector does not divide by zero");
   }

   @Test
   void healthColorGoesFromRedToGreen() {
      int empty = TargetPriority.healthColor(0.0);
      int full = TargetPriority.healthColor(1.0);
      assertEquals(255, empty >> 16 & 0xFF);
      assertEquals(0, empty >> 8 & 0xFF);
      assertEquals(0, full >> 16 & 0xFF);
      assertEquals(220, full >> 8 & 0xFF);
      assertEquals(0xFF, TargetPriority.healthColor(0.5) >>> 24);
   }
}
