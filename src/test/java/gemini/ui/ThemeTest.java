package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class ThemeTest {
   private static final ToIntFunction<String> WIDTH = s -> s.length() * 6;

   @Test
   void fitKeepsTextThatFits() {
      assertEquals("Flight", Theme.fit("Flight", 36, WIDTH));
      assertEquals("", Theme.fit(null, 36, WIDTH));
      assertEquals("", Theme.fit("", 36, WIDTH));
   }

   @Test
   void fitEllipsizesToTheWidth() {
      for (int max = 12; max <= 120; max += 7) {
         String fitted = Theme.fit("Presets & Configs dashboard page", max, WIDTH);
         assertTrue(WIDTH.applyAsInt(fitted) <= max, "fits " + max + ": " + fitted);
         assertTrue(fitted.endsWith(".."), fitted);
         assertTrue(!fitted.contains(" .."), "no space before the dots: " + fitted);
      }

      assertEquals("Pres..", Theme.fit("Presets", 41, WIDTH));
   }

   @Test
   void fitGivesUpBelowTheDots() {
      assertEquals("", Theme.fit("Anything", 11, WIDTH));
      assertEquals("..", Theme.fit("Anything", 12, WIDTH));
   }

   @Test
   void interactionStates() {
      assertEquals(Glass.TEXT_FAINT, Theme.labelColor(false, 1.0F), "disabled ignores hover");
      assertEquals(Glass.TEXT, Theme.labelColor(true, 1.0F));
      assertEquals(Theme.DISABLED_OPACITY, Theme.controlOpacity(false, 1.0F), 1.0E-6F);
      assertEquals(0.5F, Theme.controlOpacity(true, 0.5F), 1.0E-6F, "a control fades with its container");
      assertEquals(0.0F, Theme.emphasis(0.0F, -1L), 1.0E-6F);
      assertTrue(Theme.emphasis(0.0F, 0L) > Theme.emphasis(0.0F, Theme.PRESS_MS / 2), "the press flash decays");
      assertEquals(0.0F, Theme.emphasis(0.0F, Theme.PRESS_MS), 1.0E-6F);
      assertTrue(Theme.emphasis(1.0F, 0L) <= 1.0F);
   }
}
