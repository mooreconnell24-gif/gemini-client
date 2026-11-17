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
         assertTrue(brightness(Glass.TEXT) > brightness(HudStyle.label(mode)));
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
      assertEquals(Glass.TEXT, HudStyle.level(1.0F));
      assertEquals(Glass.TEXT, HudStyle.level(0.3F));
      assertEquals(Glass.WARN, HudStyle.level(0.29F));
      assertEquals(Glass.ERROR, HudStyle.level(0.11F));
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
