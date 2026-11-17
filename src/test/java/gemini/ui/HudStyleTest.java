package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudStyleTest {
   @Test
   void backdropNamesMapToConstants() {
      assertEquals(HudStyle.SOFT, HudStyle.backdrop("Soft"));
      assertEquals(HudStyle.SOLID, HudStyle.backdrop("Solid"));
      assertEquals(HudStyle.NONE, HudStyle.backdrop("None"));
      assertEquals(HudStyle.SOFT, HudStyle.backdrop("Glass"), "unknown values fall back to the default");
      assertEquals(HudStyle.SOFT, HudStyle.backdrop(null));
   }

   @Test
   void noBackdropMeansShadowAndBrighterSecondaryText() {
      assertTrue(HudStyle.shadow(HudStyle.NONE, false));
      assertFalse(HudStyle.shadow(HudStyle.SOFT, false));
      assertTrue(HudStyle.shadow(HudStyle.SOLID, true));
      assertNotEquals(HudStyle.label(HudStyle.SOFT), HudStyle.label(HudStyle.NONE));
      assertTrue(brightness(HudStyle.label(HudStyle.NONE)) > brightness(HudStyle.label(HudStyle.SOFT)));
      assertTrue(brightness(HudStyle.unit(HudStyle.NONE)) > brightness(HudStyle.unit(HudStyle.SOFT)));
      // The hierarchy holds on every backdrop: value brighter than label brighter than unit.
      for (int mode = 0; mode < 3; mode++) {
         assertTrue(brightness(Theme.current().text) > brightness(HudStyle.label(mode)));
         assertTrue(brightness(HudStyle.label(mode)) > brightness(HudStyle.unit(mode)));
      }
   }

   @Test
   void cardAlphaFollowsModeAndOpacity() {
      assertEquals(0, HudStyle.cardAlpha(HudStyle.NONE, 1.0F));
      assertTrue(HudStyle.cardAlpha(HudStyle.SOLID, 1.0F) > HudStyle.cardAlpha(HudStyle.SOFT, 1.0F));
      assertEquals(0, HudStyle.cardAlpha(HudStyle.SOFT, 0.0F));
      assertEquals(HudStyle.cardAlpha(HudStyle.SOFT, 1.0F), HudStyle.cardAlpha(HudStyle.SOFT, 4.0F));
   }

   @Test
   void levelColoursOnlyNearlyEmptyValues() {
      assertEquals(HudStyle.PRIMARY, HudStyle.level(1.0F));
      assertEquals(HudStyle.PRIMARY, HudStyle.level(0.3F));
      assertEquals(Theme.current().warn, HudStyle.level(0.29F));
      assertEquals(Theme.current().error, HudStyle.level(0.11F));
   }

   @Test
   void storedTextLevelsResolveToTheThemeAndRealColoursPassThrough() {
      Theme.Palette theme = Theme.current();
      for (int mode = 0; mode < 3; mode++) {
         assertEquals(theme.text, HudStyle.themed(HudStyle.PRIMARY, mode));
         assertEquals(HudStyle.label(mode), HudStyle.themed(HudStyle.SECONDARY, mode));
         assertEquals(HudStyle.unit(mode), HudStyle.themed(HudStyle.TERTIARY, mode));
         assertEquals(0xFF123456, HudStyle.themed(0xFF123456, mode));
         assertEquals(theme.warn, HudStyle.themed(HudStyle.level(0.2F), mode));
      }

      // The placeholders can never be mistaken for a colour someone meant to draw: they are fully transparent.
      assertEquals(0, HudStyle.PRIMARY >>> 24);
      assertEquals(0, HudStyle.SECONDARY >>> 24);
      assertEquals(0, HudStyle.TERTIARY >>> 24);
   }

   @Test
   void everyPresetKeepsTheTextHierarchyOnEveryBackdrop() {
      Theme.Palette live = Theme.current();
      Theme.Palette saved = new Theme.Palette("saved");
      saved.set(live);
      try {
         for (Theme.Palette preset : Theme.PRESETS) {
            live.set(preset);
            for (int mode = 0; mode < 3; mode++) {
               assertTrue(brightness(live.text) > brightness(HudStyle.label(mode)), preset.name());
               assertTrue(brightness(HudStyle.label(mode)) > brightness(HudStyle.unit(mode)), preset.name());
               assertEquals(0xFF, HudStyle.label(mode) >>> 24);
               assertEquals(0xFF, HudStyle.unit(mode) >>> 24);
            }
         }
      } finally {
         live.set(saved);
      }
   }

   @Test
   void listGradientRunsFromTheFirstColourToTheSecond() {
      int from = 0xFF2E86FF;
      int to = 0xFF22D3FF;
      assertEquals(from, HudStyle.gradientAt(from, to, 0, 5, 0.0F));
      assertEquals(to, HudStyle.gradientAt(from, to, 4, 5, 0.0F));
      assertEquals(Glass.lerp(from, to, 0.5F) | 0xFF000000, HudStyle.gradientAt(from, to, 2, 5, 0.0F));
      assertEquals(from, HudStyle.gradientAt(from, to, 0, 1, 0.0F), "a single row is the first colour");
      assertEquals(0xFF, HudStyle.gradientAt(from & 0xFFFFFF, to, 1, 3, 0.0F) >>> 24, "always opaque");
   }

   @Test
   void movingWaveStaysBetweenTheTwoColoursAndRepeatsEveryCycle() {
      int from = 0xFF000000;
      int to = 0xFF0000FF;
      for (float wave = 0.01F; wave < 3.0F; wave += 0.07F) {
         for (int row = 0; row < 8; row++) {
            int color = HudStyle.gradientAt(from, to, row, 8, wave);
            assertEquals(0, color & 0x00FFFF00, "only the blue channel moves");
            int later = HudStyle.gradientAt(from, to, row, 8, wave + 1.0F);
            assertTrue(Math.abs((color & 0xFF) - (later & 0xFF)) <= 2, "one whole number is one cycle");
         }
      }

      // Neighbouring rows are close in colour at any moment: the wave is continuous along the list.
      for (int row = 0; row < 7; row++) {
         int a = HudStyle.gradientAt(from, to, row, 8, 0.37F) & 0xFF;
         int b = HudStyle.gradientAt(from, to, row + 1, 8, 0.37F) & 0xFF;
         assertTrue(Math.abs(a - b) <= 40, "row " + row);
      }
   }

   @Test
   void fillWidthShowsASliverButNeverOverflows() {
      assertEquals(0, HudStyle.fillWidth(40, 0.0F));
      assertEquals(0, HudStyle.fillWidth(40, Float.NaN));
      assertEquals(1, HudStyle.fillWidth(40, 0.001F));
      assertEquals(20, HudStyle.fillWidth(40, 0.5F));
      assertEquals(40, HudStyle.fillWidth(40, 1.7F));
      assertEquals(0, HudStyle.fillWidth(0, 0.5F));
   }

   @Test
   void edgeFadeIsFullInsideAndZeroAtTheEnds() {
      assertEquals(1.0F, HudStyle.edgeFade(100.0F, 200.0F, 40.0F));
      assertEquals(0.0F, HudStyle.edgeFade(0.0F, 200.0F, 40.0F));
      assertEquals(0.0F, HudStyle.edgeFade(-5.0F, 200.0F, 40.0F));
      assertEquals(0.0F, HudStyle.edgeFade(200.0F, 200.0F, 40.0F));
      float near = HudStyle.edgeFade(10.0F, 200.0F, 40.0F);
      float nearer = HudStyle.edgeFade(5.0F, 200.0F, 40.0F);
      assertTrue(near > nearer && nearer > 0.0F && near < 1.0F);
      assertEquals(HudStyle.edgeFade(30.0F, 200.0F, 40.0F), HudStyle.edgeFade(170.0F, 200.0F, 40.0F), 1.0E-6F);
   }

   private static int brightness(int argb) {
      return (argb >> 16 & 0xFF) + (argb >> 8 & 0xFF) + (argb & 0xFF);
   }
}
