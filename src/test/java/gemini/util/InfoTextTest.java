package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class InfoTextTest {
   @Test
   void formatsWithADecimalPointWhateverTheSystemLocale() {
      Locale before = Locale.getDefault();
      try {
         Locale.setDefault(Locale.GERMANY);
         assertEquals("0.55", new InfoText().fixed(0.55, 2));
         assertEquals("1.5m", new InfoText().fixed(1.5, 1, "m"));
      } finally {
         Locale.setDefault(before);
      }
   }

   @Test
   void theSameValueReturnsTheSameStringWithoutReformatting() {
      InfoText text = new InfoText();
      String first = text.fixed(2.25, 2, "x");
      assertSame(first, text.fixed(2.25, 2, "x"));
      assertEquals("2.50x", text.fixed(2.5, 2, "x"));
   }

   @Test
   void clockAndWholeNumbers() {
      InfoText text = new InfoText();
      assertEquals("1:05", text.clock(65));
      assertEquals("0:00", text.clock(-4));
      assertEquals("12 · Tower", new InfoText().whole(12, " · Tower"));
      assertEquals("-", InfoText.format(Double.NaN, 1));
   }

   @Test
   void switchingBetweenClockAndFixedDoesNotReuseTheWrongText() {
      InfoText text = new InfoText();
      assertEquals("0:05", text.clock(5));
      assertEquals("5", text.fixed(5, 0));
      assertEquals("0:05", text.clock(5));
   }
}
