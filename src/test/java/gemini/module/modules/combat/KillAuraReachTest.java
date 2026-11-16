package gemini.module.modules.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KillAuraReachTest {
   @Test
   void boxDistanceIsZeroInsideAndEuclideanOutside() {
      assertEquals(0.0, KillAura.boxDistance(0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
      assertEquals(2.0, KillAura.boxDistance(3, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
      assertEquals(5.0, KillAura.boxDistance(-3, 0.5, -4, 0, 0, 0, 1, 1, 1), 1e-9, "corner distance");
      assertEquals(1.0, KillAura.boxDistance(0.5, 3, 0.5, 0, 0, 0, 1, 2, 1), 1e-9);
   }
}
