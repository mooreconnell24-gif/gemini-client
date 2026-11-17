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

   @Test
   void timerBarEmptiesJustAsTheCardStartsToLeave() {
      long duration = 3000L;
      assertEquals(1.0F, HudOverlay.timeLeft(0L, duration));
      assertEquals(1.0F, HudOverlay.timeLeft(-50L, duration));
      float previous = 1.0F;
      for (long t = 0L; t <= duration; t += 20L) {
         float left = HudOverlay.timeLeft(t, duration);
         assertTrue(left <= previous && left >= 0.0F, "only ever runs down");
         previous = left;
      }

      assertEquals(0.5F, HudOverlay.timeLeft(1390L, duration), 0.01F);
      assertEquals(0.0F, HudOverlay.timeLeft(duration, duration));
      // Empty while the card is still fully on screen, so the bar is seen to finish.
      long emptyAt = duration - 220L;
      assertEquals(0.0F, HudOverlay.timeLeft(emptyAt, duration));
      assertEquals(1.0F, HudOverlay.toastSlide(emptyAt - 1L, duration));
      assertEquals(0.0F, HudOverlay.timeLeft(100L, 100L), "a card too short to have a hold phase shows no bar");
   }

   @Test
   void everySeverityHasAThemeColourAndAnIcon() {
      for (Theme.Palette preset : Theme.PRESETS) {
         for (HudOverlay.Severity severity : HudOverlay.Severity.values()) {
            assertEquals(0xFF, severity.color(preset) >>> 24);
            assertTrue(severity.icon >= HudShapes.CHECK && severity.icon <= HudShapes.WARNING);
         }

         assertEquals(preset.accent | 0xFF000000, HudOverlay.Severity.INFO.color(preset), "information is the accent");
         assertEquals(preset.accent | 0xFF000000, HudOverlay.Severity.ENABLED.color(preset));
         assertEquals(preset.error | 0xFF000000, HudOverlay.Severity.ERROR.color(preset));
         assertEquals(preset.warn | 0xFF000000, HudOverlay.Severity.WARNING.color(preset));
         assertEquals(preset.ok | 0xFF000000, HudOverlay.Severity.SUCCESS.color(preset));
      }
   }

   @Test
   void notificationsStartPastTheWidgetsInTheirCorner() {
      // nothing in the corner: at the margin
      assertEquals(6.0F, HudOverlay.clearOf(new float[0], 0, 6.0F, 32.0F, 4.0F, 200.0F));
      // one widget from 4 to 20 above the bottom: the stack starts above it, a gap later
      assertEquals(24.0F, HudOverlay.clearOf(new float[] {4.0F, 20.0F}, 1, 6.0F, 32.0F, 4.0F, 200.0F));
      // a column of widgets, given in any order, is climbed whole
      float[] column = {40.0F, 56.0F, 4.0F, 20.0F, 22.0F, 38.0F};
      assertEquals(60.0F, HudOverlay.clearOf(column, 3, 6.0F, 32.0F, 4.0F, 200.0F));
      // a widget far enough away leaves room for a card under it
      assertEquals(6.0F, HudOverlay.clearOf(new float[] {60.0F, 90.0F}, 1, 6.0F, 32.0F, 4.0F, 200.0F));
      // but not when the card would not fit in between
      assertEquals(94.0F, HudOverlay.clearOf(new float[] {30.0F, 90.0F}, 1, 6.0F, 32.0F, 4.0F, 200.0F));
      // a corner that is full to past the limit is not climbed at all
      assertEquals(6.0F, HudOverlay.clearOf(new float[] {0.0F, 300.0F}, 1, 6.0F, 32.0F, 4.0F, 200.0F));
      // nor is one that cannot be cleared within the limit half climbed
      assertEquals(6.0F, HudOverlay.clearOf(new float[] {4.0F, 20.0F, 30.0F, 300.0F}, 2, 6.0F, 32.0F, 4.0F, 200.0F));
   }
}
