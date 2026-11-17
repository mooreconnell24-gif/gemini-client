package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pure coverage maths behind the notification icons and the health ring. */
class HudShapesTest {
   private static float ink(int icon, int size) {
      float total = 0.0F;
      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            total += HudShapes.iconCoverage(icon, x, y, size);
         }
      }

      return total / (size * size);
   }

   @Test
   void everyIconHasInkButIsMostlyEmptyAndKeepsItsMarginClear() {
      for (int icon = HudShapes.CHECK; icon <= HudShapes.WARNING; icon++) {
         for (int size : new int[] {10, 20, 30, 40}) {
            float ink = ink(icon, size);
            assertTrue(ink > 0.05F && ink < 0.45F, "icon " + icon + " at " + size + " px inks " + ink);
            for (int i = 0; i < size; i++) {
               assertEquals(0.0F, HudShapes.iconCoverage(icon, i, 0, size), "top edge");
               assertEquals(0.0F, HudShapes.iconCoverage(icon, i, size - 1, size), "bottom edge");
               assertEquals(0.0F, HudShapes.iconCoverage(icon, 0, i, size), "left edge");
               assertEquals(0.0F, HudShapes.iconCoverage(icon, size - 1, i, size), "right edge");
            }
         }
      }
   }

   @Test
   void iconsLookTheSameAtEverySize() {
      // The share of the square that is inked barely changes with resolution: the shapes scale, they are not redrawn.
      for (int icon = HudShapes.CHECK; icon <= HudShapes.WARNING; icon++) {
         assertEquals(ink(icon, 20), ink(icon, 40), 0.02F, "icon " + icon);
      }
   }

   @Test
   void crossIsSymmetricAndInfoAndWarningAreCentred() {
      int size = 32;
      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            assertEquals(HudShapes.iconCoverage(HudShapes.CROSS, x, y, size), HudShapes.iconCoverage(HudShapes.CROSS, size - 1 - x, y, size), 1.0E-4F);
            assertEquals(HudShapes.iconCoverage(HudShapes.CROSS, x, y, size), HudShapes.iconCoverage(HudShapes.CROSS, x, size - 1 - y, size), 1.0E-4F);
            assertEquals(HudShapes.iconCoverage(HudShapes.INFO, x, y, size), HudShapes.iconCoverage(HudShapes.INFO, size - 1 - x, y, size), 1.0E-4F);
            assertEquals(HudShapes.iconCoverage(HudShapes.WARNING, x, y, size), HudShapes.iconCoverage(HudShapes.WARNING, size - 1 - x, y, size), 1.0E-4F);
         }
      }

      // The stroke of the cross passes through the middle.
      assertEquals(1.0F, HudShapes.iconCoverage(HudShapes.CROSS, 16, 16, size));
   }

   @Test
   void ringCoversItsBandAndNothingElse() {
      int radius = 30;
      float stroke = 6.0F;
      assertEquals(1.0F, HudShapes.ringCoverage(radius, 2, radius, stroke), "inside the band at the top");
      assertEquals(0.0F, HudShapes.ringCoverage(radius, radius, radius, stroke), "the centre is empty");
      assertEquals(0.0F, HudShapes.ringCoverage(radius, 10, radius, stroke), "inside the hole");
      assertEquals(0.0F, HudShapes.ringCoverage(0, 0, radius, stroke), "the corner of the square");
      // Its area is that of the band, to within the antialiasing.
      double area = 0.0;
      for (int y = 0; y < radius * 2; y++) {
         for (int x = 0; x < radius * 2; x++) {
            area += HudShapes.ringCoverage(x, y, radius, stroke);
         }
      }

      double expected = Math.PI * (radius * radius - (radius - stroke) * (radius - stroke));
      assertEquals(expected, area, expected * 0.02);
   }

   @Test
   void ringAngleStartsAtTheTopAndGrowsClockwise() {
      int radius = 20;
      float top = HudShapes.ringAngle(radius, 0, radius);
      float right = HudShapes.ringAngle(radius * 2 - 1, radius, radius);
      float bottom = HudShapes.ringAngle(radius, radius * 2 - 1, radius);
      float left = HudShapes.ringAngle(0, radius, radius);
      assertEquals(0.0F, top, 0.01F);
      assertEquals(0.25F, right, 0.01F);
      assertEquals(0.5F, bottom, 0.01F);
      assertEquals(0.75F, left, 0.01F);
      assertTrue(HudShapes.ringAngle(radius - 1, 0, radius) > 0.98F, "just left of the top is the end of the turn");
   }

   @Test
   void arcLightsWhatLiesBeforeItsEnd() {
      float circumference = 120.0F;
      assertEquals(1.0F, HudShapes.arcCoverage(0.2F, 0.5F, circumference));
      assertEquals(0.0F, HudShapes.arcCoverage(0.8F, 0.5F, circumference));
      assertEquals(0.5F, HudShapes.arcCoverage(0.5F, 0.5F, circumference), 1.0E-4F, "the end is blended over one pixel");
      assertEquals(1.0F, HudShapes.arcCoverage(0.999F, 1.0F, circumference), "a full ring has no gap at the seam");
      assertEquals(1.0F, HudShapes.arcCoverage(0.0F, 1.0F, circumference));
      assertEquals(0.0F, HudShapes.arcCoverage(0.0F, 0.0F, circumference), "an empty ring lights nothing");
      // More health never lights less of any pixel.
      for (float angle = 0.0F; angle < 1.0F; angle += 0.05F) {
         float previous = 0.0F;
         for (float fraction = 0.0F; fraction <= 1.0F; fraction += 0.02F) {
            float lit = HudShapes.arcCoverage(angle, fraction, circumference);
            assertTrue(lit >= previous - 1.0E-6F);
            previous = lit;
         }
      }
   }
}
