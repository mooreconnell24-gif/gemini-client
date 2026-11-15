package gemini.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlacementFadeTest {
   @Test
   void theFadeStartsFullEndsEmptyAndNeverBrightens() {
      assertEquals(1.0F, PlacementFade.fadeCurve(0.0F), 1.0E-6F);
      assertEquals(0.0F, PlacementFade.fadeCurve(1.0F), 1.0E-6F);
      float previous = 1.0F;
      for (int i = 1; i <= 100; i++) {
         float value = PlacementFade.fadeCurve(i / 100.0F);
         assertTrue(value <= previous + 1.0E-6F, "step " + i);
         assertTrue(value >= 0.0F && value <= 1.0F);
         previous = value;
      }
   }

   @Test
   void outOfRangeProgressIsClamped() {
      assertEquals(1.0F, PlacementFade.fadeCurve(-3.0F), 1.0E-6F);
      assertEquals(0.0F, PlacementFade.fadeCurve(7.0F), 1.0E-6F);
   }
}
