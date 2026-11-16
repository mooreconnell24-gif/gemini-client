package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToastCurveTest {
   @Test
   void slidesInWithoutOvershootHoldsThenEasesOut() {
      long duration = 3000L;
      assertEquals(0.0F, HudOverlay.toastSlide(0L, duration), 1.0E-4F);
      float previous = 0.0F;
      for (long t = 0L; t < 400L; t += 10L) {
         float slide = HudOverlay.toastSlide(t, duration);
         assertTrue(slide <= 1.0F, "never slides past its place");
         assertTrue(slide >= previous, "slides in monotonically");
         previous = slide;
      }

      assertEquals(1.0F, HudOverlay.toastSlide(1500L, duration));
      float early = HudOverlay.toastSlide(duration - 250L, duration);
      float late = HudOverlay.toastSlide(duration - 20L, duration);
      assertTrue(early > late && late > 0.0F, "slides out monotonically");
      assertTrue(1.0F - early < early - late, "ease-in: slow start, fast finish");
      assertEquals(0.0F, HudOverlay.toastSlide(duration, duration), 1.0E-4F);
   }
}
