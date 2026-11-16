package gemini.module.modules.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ElytraRecastTest {
   @Test
   void neverReopensAnOpenElytra() {
      assertFalse(ElytraRecast.shouldRedeploy(true, 10, 0, false, -0.5, 0.0));
   }

   @Test
   void instantModeWaitsOnlyForTheDelay() {
      assertTrue(ElytraRecast.shouldRedeploy(false, 1, 0, false, 0.4, 0.0), "rising is fine in Instant mode");
      assertFalse(ElytraRecast.shouldRedeploy(false, 3, 3, false, -0.5, 0.0));
      assertTrue(ElytraRecast.shouldRedeploy(false, 4, 3, false, -0.5, 0.0));
   }

   @Test
   void fallingModeNeedsDownwardSpeed() {
      assertFalse(ElytraRecast.shouldRedeploy(false, 5, 0, true, 0.2, 0.0), "still rising");
      assertFalse(ElytraRecast.shouldRedeploy(false, 5, 0, true, 0.0, 0.0), "the top of the jump is not a fall");
      assertTrue(ElytraRecast.shouldRedeploy(false, 5, 0, true, -0.01, 0.0));
      assertFalse(ElytraRecast.shouldRedeploy(false, 5, 0, true, -0.2, 0.3));
      assertTrue(ElytraRecast.shouldRedeploy(false, 5, 0, true, -0.3, 0.3));
   }

   @Test
   void durabilityStop() {
      assertTrue(ElytraRecast.durabilityLow(true, 432, 422, 10));
      assertFalse(ElytraRecast.durabilityLow(true, 432, 421, 10));
      assertFalse(ElytraRecast.durabilityLow(false, 0, 0, 10), "unbreakable items never stop the module");
   }
}
