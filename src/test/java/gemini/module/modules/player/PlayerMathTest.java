package gemini.module.modules.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerMathTest {
   @Test
   void healthyItemsDoNotAlert() {
      assertEquals(PlayerMath.ALERT_NONE, PlayerMath.durabilityAlert(1500, 1561, 10.0, 5));
      assertEquals(PlayerMath.ALERT_NONE, PlayerMath.durabilityAlert(157, 1561, 10.0, 5));
   }

   @Test
   void belowThePercentageWarns() {
      assertEquals(PlayerMath.ALERT_WARN, PlayerMath.durabilityAlert(155, 1561, 10.0, 5));
   }

   @Test
   void fewUsesLeftIsCriticalEvenAboveThePercentage() {
      // A 25-use item with 5 left is at 20%: above a 10% warning, but still about to break.
      assertEquals(PlayerMath.ALERT_CRITICAL, PlayerMath.durabilityAlert(5, 25, 10.0, 5));
      assertEquals(PlayerMath.ALERT_CRITICAL, PlayerMath.durabilityAlert(1, 1561, 10.0, 5));
      assertEquals(PlayerMath.ALERT_WARN, PlayerMath.durabilityAlert(6, 1561, 10.0, 5));
   }

   @Test
   void itemsWithoutDurabilityNeverAlert() {
      assertEquals(PlayerMath.ALERT_NONE, PlayerMath.durabilityAlert(0, 0, 50.0, 5));
   }
}
