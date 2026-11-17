package gemini.ui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class TextFitTest {
   /** A proportional stand-in font: narrow letters are 2 wide, the ellipsis 5, everything else 6. */
   private static final ToIntFunction<String> WIDTH = s -> {
      int w = 0;
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         w += c == 'i' || c == 'l' || c == '.' || c == ' ' ? 2 : (c == '…' ? 5 : 6);
      }

      return w;
   };

   @Test
   void textThatFitsIsReturnedAsIs() {
      String text = "Flight";
      assertSame(text, TextFit.ellipsize(text, WIDTH.applyAsInt(text), WIDTH, TextFit.ELLIPSIS));
      assertEquals("", TextFit.ellipsize(null, 50, WIDTH, TextFit.ELLIPSIS));
      assertEquals("", TextFit.ellipsize("", 50, WIDTH, TextFit.ELLIPSIS));
      assertEquals("", TextFit.ellipsize("Flight", 0, WIDTH, TextFit.ELLIPSIS));
   }

   @Test
   void cutTextNeverExceedsTheWidthAndIsAsLongAsPossible() {
      String text = "Profiles and shared settings page";
      for (int max = 5; max < WIDTH.applyAsInt(text); max++) {
         String fitted = TextFit.ellipsize(text, max, WIDTH, TextFit.ELLIPSIS);
         assertTrue(WIDTH.applyAsInt(fitted) <= max, max + ": " + fitted);
         assertTrue(fitted.endsWith(TextFit.ELLIPSIS), fitted);
         assertFalse(fitted.contains(" " + TextFit.ELLIPSIS), "no space before the ellipsis: " + fitted);
         // one more character of the original would not have fitted
         int kept = fitted.length() - 1;
         String longer = text.substring(0, Math.min(text.length(), kept + 1)).stripTrailing();
         if (longer.length() > kept) {
            assertTrue(WIDTH.applyAsInt(longer + TextFit.ELLIPSIS) > max, max + ": could have kept " + longer);
         }
      }
   }

   @Test
   void nothingIsReturnedWhenEvenTheEllipsisIsTooWide() {
      assertEquals("", TextFit.ellipsize("Flight", 4, WIDTH, TextFit.ELLIPSIS));
      assertEquals(TextFit.ELLIPSIS, TextFit.ellipsize("Flight", 5, WIDTH, TextFit.ELLIPSIS));
      assertEquals("", TextFit.ellipsize("Flight", 3, WIDTH, TextFit.DOTS));
      assertEquals("..", TextFit.ellipsize("Flight", 4, WIDTH, TextFit.DOTS));
   }

   @Test
   void surrogatePairsAreNotSplit() {
      String text = "ab😀😀😀cd";
      for (int max = 5; max < WIDTH.applyAsInt(text); max++) {
         String fitted = TextFit.ellipsize(text, max, WIDTH, TextFit.ELLIPSIS);
         for (int i = 0; i < fitted.length(); i++) {
            if (Character.isHighSurrogate(fitted.charAt(i))) {
               assertTrue(i + 1 < fitted.length() && Character.isLowSurrogate(fitted.charAt(i + 1)), "split pair in " + fitted);
            }
         }
      }
   }

   @Test
   void wrapBreaksAtSpacesAndKeepsEveryWord() {
      String text = "Smooth draws the menus and HUD in a clean sans-serif";
      List<String> lines = TextFit.wrap(text, 90, 0, WIDTH, TextFit.ELLIPSIS);
      assertTrue(lines.size() > 2);
      for (String line : lines) {
         assertTrue(WIDTH.applyAsInt(line) <= 90, line);
         assertEquals(line.strip(), line);
      }

      assertEquals(text, String.join(" ", lines));
   }

   @Test
   void wrapSplitsAWordThatIsTooWideOnItsOwn() {
      List<String> lines = TextFit.wrap("Supercalifragilistic", 30, 0, WIDTH, TextFit.ELLIPSIS);
      assertTrue(lines.size() > 1);
      for (String line : lines) {
         assertTrue(WIDTH.applyAsInt(line) <= 30, line);
         assertFalse(line.isEmpty());
      }

      assertEquals("Supercalifragilistic", String.join("", lines));
   }

   @Test
   void wrapHonoursNewlinesAndTheLineLimit() {
      assertEquals(List.of("one", "", "two"), TextFit.wrap("one\n\ntwo", 100, 0, WIDTH, TextFit.ELLIPSIS));
      List<String> limited = TextFit.wrap("alpha beta gamma delta epsilon zeta eta theta", 60, 2, WIDTH, TextFit.ELLIPSIS);
      assertEquals(2, limited.size());
      assertTrue(limited.get(1).endsWith(TextFit.ELLIPSIS), limited.get(1));
      assertTrue(WIDTH.applyAsInt(limited.get(1)) <= 60);
      // text that fits in the limit is not marked as cut
      List<String> whole = TextFit.wrap("alpha beta", 60, 2, WIDTH, TextFit.ELLIPSIS);
      assertFalse(whole.get(whole.size() - 1).endsWith(TextFit.ELLIPSIS));
      assertTrue(TextFit.wrap("", 60, 2, WIDTH, TextFit.ELLIPSIS).isEmpty());
      assertTrue(TextFit.wrap("text", 0, 2, WIDTH, TextFit.ELLIPSIS).isEmpty());
   }
}
