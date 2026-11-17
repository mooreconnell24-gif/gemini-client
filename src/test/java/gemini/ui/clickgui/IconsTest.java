package gemini.ui.clickgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The drawn icons: distance maths and the rasterisation that turns them into antialiased masks. */
class IconsTest {
   @Test
   void distanceToASegment() {
      assertEquals(0.0F, Icons.segment(0.5F, 0.5F, 0.0F, 0.5F, 1.0F, 0.5F), 1.0E-6F, "on the line");
      assertEquals(0.25F, Icons.segment(0.5F, 0.75F, 0.0F, 0.5F, 1.0F, 0.5F), 1.0E-6F, "beside it");
      assertEquals(0.5F, Icons.segment(1.5F, 0.5F, 0.0F, 0.5F, 1.0F, 0.5F), 1.0E-6F, "past its end: distance to the end point");
      assertEquals(1.0F, Icons.segment(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F), 1.0E-6F, "a zero-length segment is a point");
   }

   @Test
   void polygonDistanceIsNegativeInside() {
      float[] square = {0.2F, 0.2F, 0.8F, 0.2F, 0.8F, 0.8F, 0.2F, 0.8F};
      assertEquals(-0.3F, Icons.polygon(0.5F, 0.5F, square), 1.0E-5F);
      assertEquals(0.1F, Icons.polygon(0.9F, 0.5F, square), 1.0E-5F);
      assertEquals(0.0F, Icons.polygon(0.8F, 0.5F, square), 1.0E-5F);
   }

   @Test
   void everyIconHasInkAndStaysInsideItsSquare() {
      for (Icons.Icon icon : Icons.Icon.values()) {
         for (int pixels : new int[]{8, 10, 16, 20, 30}) {
            float ink = 0.0F;
            float edge = 0.0F;
            for (int y = 0; y < pixels; y++) {
               for (int x = 0; x < pixels; x++) {
                  float coverage = icon.coverage(x, y, pixels);
                  assertTrue(coverage >= 0.0F && coverage <= 1.0F);
                  ink += coverage;
                  if (x == 0 || y == 0 || x == pixels - 1 || y == pixels - 1) {
                     edge = Math.max(edge, coverage);
                  }
               }
            }

            float share = ink / (pixels * pixels);
            assertTrue(share > 0.08F, icon + " at " + pixels + " px is nearly empty: " + share);
            assertTrue(share < 0.6F, icon + " at " + pixels + " px is a blob: " + share);
            if (pixels >= 16) {
               assertTrue(edge < 0.6F, icon + " at " + pixels + " px touches the edge of its square");
            }
         }
      }
   }

   @Test
   void edgesAreAntialiased() {
      // a 30 px search icon has pixels that are neither empty nor full along its ring
      int partial = 0;
      for (int y = 0; y < 30; y++) {
         for (int x = 0; x < 30; x++) {
            float coverage = Icons.Icon.SEARCH.coverage(x, y, 30);
            if (coverage > 0.1F && coverage < 0.9F) {
               partial++;
            }
         }
      }

      assertTrue(partial > 20, "soft edge pixels: " + partial);
   }

   @Test
   void theFilledStarIsSolidWhereTheOutlineIsHollow() {
      assertTrue(Icons.Icon.STAR.distance(0.5F, 0.53F) < 0.0F, "centre of the filled star is ink");
      assertTrue(Icons.Icon.STAR_OUTLINE.distance(0.5F, 0.53F) > 0.0F, "centre of the outline is empty");
      assertTrue(Icons.Icon.STAR_OUTLINE.distance(0.5F, 0.09F) < 0.0F, "its top point is ink");
   }

   @Test
   void strokesStaySolidWhenSmall() {
      // a diagonal stroke through the middle of the check mark is still fully covered at 8 px
      float best = 0.0F;
      for (int y = 0; y < 8; y++) {
         for (int x = 0; x < 8; x++) {
            best = Math.max(best, Icons.Icon.CHECK.coverage(x, y, 8));
         }
      }

      assertTrue(best > 0.9F, "peak coverage " + best);
   }

   @Test
   void categoriesAndPagesHaveTheirOwnIcon() {
      for (String name : new String[]{"COMBAT", "MOVEMENT", "PLAYER", "WORLD", "RENDER", "HUD", "CLIENT", "PROFILES", "KEYBINDS", "BARITONE", "MODS"}) {
         assertEquals(name, Icons.named(name).name());
      }

      assertEquals(Icons.Icon.CLIENT, Icons.named("SOMETHING_NEW"), "unknown names fall back");
   }
}
