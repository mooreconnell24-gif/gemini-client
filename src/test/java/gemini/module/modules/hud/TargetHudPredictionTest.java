package gemini.module.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TargetHudPredictionTest {
   @Test
   void fasterKillWins() {
      // I kill them in 20/8 = 2.5 s; they kill me in 20/4 = 5 s.
      assertEquals(1, TargetHud.predict(20.0F, 8.0F, 20.0F, 4.0F));
      assertEquals(-1, TargetHud.predict(20.0F, 4.0F, 20.0F, 8.0F));
   }

   @Test
   void closeFightsAreEven() {
      assertEquals(0, TargetHud.predict(20.0F, 5.0F, 22.0F, 5.0F));
   }

   @Test
   void absorptionCountsAsHealth() {
      assertEquals(1, TargetHud.predict(36.0F, 5.0F, 20.0F, 5.0F));
   }

   @Test
   void harmlessSides() {
      assertEquals(0, TargetHud.predict(20.0F, 0.0F, 20.0F, 0.0F));
      assertEquals(1, TargetHud.predict(20.0F, 2.0F, 20.0F, 0.0F), "a target that cannot hurt you is a win");
      assertEquals(-1, TargetHud.predict(20.0F, 0.0F, 20.0F, 2.0F));
   }
}
