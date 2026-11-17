package gemini.ui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FontVariantsTest {
   @Test
   void densityIsTheNearestWholePixelsPerUnit() {
      assertEquals(1, FontVariants.density(1, 1.0F));
      assertEquals(2, FontVariants.density(2, 1.0F));
      assertEquals(3, FontVariants.density(3, 1.0F));
      assertEquals(4, FontVariants.density(4, 1.0F));
      // a menu scaled 1.5x at GUI scale 2 uses the 3x bitmaps
      assertEquals(3, FontVariants.density(2, 1.5F));
      // in between, the next density up is scaled down rather than a smaller one scaled up
      assertEquals(3, FontVariants.density(3, 0.75F));
      assertEquals(2, FontVariants.density(2, 0.6F));
      assertEquals(2, FontVariants.density(2, 0.94F));
      // within a few percent of a whole number it is that number (and the text is snapped to it)
      assertEquals(1, FontVariants.density(2, 0.52F));
      assertEquals(2, FontVariants.density(2, 1.02F));
      // without linear filtering scaling down would drop pixels, so the nearest density is used
      assertEquals(2, FontVariants.density(3, 0.75F, false));
      assertEquals(1, FontVariants.density(2, 0.6F, false));
      assertEquals(3, FontVariants.density(2, 1.5F, false));
      // never below 1 or above what has a definition
      assertEquals(1, FontVariants.density(1, 0.3F));
      assertEquals(FontVariants.MAX_DENSITY, FontVariants.density(9, 1.0F));
      assertEquals(2, FontVariants.density(2, Float.NaN));
      assertEquals(2, FontVariants.density(2, 0.0F));
      assertEquals(1, FontVariants.density(0, 1.0F));
   }

   @Test
   void everyVariantHasItsOwnIndexAndPath() {
      Set<Integer> indices = new HashSet<>();
      Set<String> paths = new HashSet<>();
      for (FontSize size : FontSize.values()) {
         for (FontWeight weight : FontWeight.values()) {
            for (int density = 1; density <= FontVariants.MAX_DENSITY; density++) {
               int index = FontVariants.index(size, weight, density);
               assertTrue(index >= 0 && index < FontVariants.count(), "index in range");
               assertTrue(indices.add(index), "unique index");
               assertTrue(paths.add(FontVariants.path(size, weight, density)), "unique path");
            }
         }
      }

      assertEquals(FontVariants.count(), indices.size());
      assertEquals("ui_body_regular_x2", FontVariants.path(FontSize.BODY, FontWeight.REGULAR, 2));
      assertEquals("ui_title_bold_x6", FontVariants.path(FontSize.TITLE, FontWeight.BOLD, 99));
      assertEquals(FontVariants.index(FontSize.SMALL, FontWeight.MEDIUM, 1), FontVariants.index(FontSize.SMALL, FontWeight.MEDIUM, 0));
   }

   /** The jar must contain a definition for every variant the code can ask for, each naming a bundled font file. */
   @Test
   void everyVariantHasAFontDefinitionInTheJar() throws IOException {
      for (FontSize size : FontSize.values()) {
         for (FontWeight weight : FontWeight.values()) {
            for (int density = 1; density <= FontVariants.MAX_DENSITY; density++) {
               String resource = "/assets/gemini/font/" + FontVariants.path(size, weight, density) + ".json";
               String json = read(resource);
               assertNotNull(json, resource);
               assertTrue(json.contains("\"type\": \"ttf\""), resource);
               assertTrue(json.contains("\"oversample\": " + density + ".0"), resource + " is rasterised at its density");
               int at = json.indexOf("gemini:inter_");
               assertTrue(at > 0, resource);
               String file = json.substring(at + "gemini:".length(), json.indexOf('"', at));
               try (InputStream font = FontVariantsTest.class.getResourceAsStream("/assets/gemini/font/" + file)) {
                  assertNotNull(font, file);
               }

               // characters the font lacks fall back to the game font
               assertTrue(json.contains("minecraft:default"), resource);
            }
         }
      }
   }

   @Test
   void theFontLicenceShipsWithTheFont() throws IOException {
      String licence = read("/assets/gemini/font/inter_ofl.txt");
      assertNotNull(licence);
      assertTrue(licence.contains("SIL Open Font License"));
      assertTrue(licence.contains("The Inter Project Authors"));
   }

   private static String read(String resource) throws IOException {
      try (InputStream in = FontVariantsTest.class.getResourceAsStream(resource)) {
         return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   @Test
   void shadowIsAWholeNumberOfScreenPixels() {
      for (int density = 1; density <= FontVariants.MAX_DENSITY; density++) {
         float pixels = FontVariants.shadowOffset(density) * density;
         assertEquals(Math.round(pixels), pixels, 1.0E-5F, "density " + density);
         assertTrue(pixels >= 1.0F);
         assertTrue(FontVariants.shadowOffset(density) <= 1.0F, "never further than the vanilla shadow");
      }

      assertEquals(1.0F, FontVariants.shadowOffset(1));
      assertEquals(0.5F, FontVariants.shadowOffset(2));
      assertEquals(0.5F, FontVariants.shadowOffset(4));
      assertEquals(0, FontVariants.shadowColor(0xFFFFFFFF) & 0xFFFFFF, "black");
      assertEquals(153, FontVariants.shadowColor(0xFFFFFFFF) >>> 24);
      assertEquals(0, FontVariants.shadowColor(0x00FFFFFF) >>> 24, "invisible text casts no shadow");
      assertTrue(FontVariants.shadowColor(0x80FFFFFF) >>> 24 < 0x80);
   }

   @Test
   void sizesGrowAndBothFontModesAgreeOnLayout() {
      FontSize previous = null;
      for (FontSize size : FontSize.values()) {
         assertTrue(size.capHeight(true) > 0 && size.capHeight(false) > 0);
         assertTrue(size.lineHeight() > size.capHeight(true), size + " leaves room between lines (smooth)");
         assertTrue(size.lineHeight() > size.capHeight(false), size + " leaves room between lines (Minecraft)");
         // the two modes are within two units of each other in height, so one layout serves both
         assertTrue(Math.abs(size.capHeight(true) - size.capHeight(false)) <= 2, size.toString());
         // a capital is about 0.73 em in the smooth font and 7 px in the pixel font
         assertEquals(size.points() * 0.7275F, size.capHeight(true), 0.75F, size.toString());
         assertEquals(7.0F * size.pixelScale(), size.capHeight(false), 0.5F, size.toString());
         if (previous != null) {
            assertTrue(size.points() > previous.points());
            assertTrue(size.lineHeight() > previous.lineHeight());
            assertTrue(size.pixelScale() >= previous.pixelScale());
         }

         previous = size;
      }

      assertEquals(1.0F, FontSize.BODY.pixelScale());
      assertEquals(11, FontSize.BODY.lineHeight());
   }

   @Test
   void textUnderANearlyWholeScaleIsSnappedToThePixelGrid() {
      // GUI scale 2 under a 0.52 pose: 1.04 pixels per unit, drawn 1:1 from the 1x bitmaps
      float snap = FontVariants.snapScale(1.04F, 1, 103, 100);
      assertEquals(1.0F / 1.04F, snap, 1.0E-6F);
      assertEquals(1.0F, 1.04F * snap, 1.0E-6F, "exactly one screen pixel per bitmap pixel");
      // an exact whole scale also "snaps" (only the origin is rounded)
      assertEquals(1.0F, FontVariants.snapScale(3.0F, 3, 100, 100));
      // too far from a whole number: resampled instead of shrinking or growing the text visibly
      assertEquals(0.0F, FontVariants.snapScale(1.5F, 2, 100, 100));
      assertEquals(0.0F, FontVariants.snapScale(1.3F, 1, 100, 100));
      // or when the snapped text would be much wider than the room the layout gave it
      assertEquals(0.0F, FontVariants.snapScale(1.9F, 2, 100, 100));
      assertEquals(0.0F, FontVariants.snapScale(1.95F, 2, 120, 100));
      assertTrue(FontVariants.snapScale(1.95F, 2, 100, 100) > 0.0F);
      assertEquals(0.0F, FontVariants.snapScale(0.0F, 1, 100, 100));
      assertEquals(0.0F, FontVariants.snapScale(Float.NaN, 1, 100, 100));
      for (float pixels = 0.9F; pixels < 6.5F; pixels += 0.01F) {
         int density = FontVariants.density(1, pixels);
         float s = FontVariants.snapScale(pixels, density, 100, 100);
         if (s > 0.0F) {
            assertEquals(density, pixels * s, 1.0E-4F);
            assertTrue(Math.abs(s - 1.0F) <= FontVariants.SNAP_TOLERANCE + 1.0E-6F);
         }
      }
   }

   @Test
   void scaledWidthRoundsUp() {
      assertEquals(40, FontVariants.scaledWidth(40, 1.0F));
      assertEquals(60, FontVariants.scaledWidth(40, 1.5F));
      assertEquals(62, FontVariants.scaledWidth(41, 1.5F));
      assertEquals(82, FontVariants.scaledWidth(41, 2.0F));
      assertEquals(0, FontVariants.scaledWidth(0, 3.0F));
   }

   @Test
   void centerYCentresTheCapitals() {
      // 7 px capitals in a 20 px button: 6 above, 7 below (the extra pixel goes under, where descenders are)
      assertEquals(106, FontVariants.centerY(100, 20, 7));
      assertEquals(104, FontVariants.centerY(100, 16, 7));
      assertEquals(100, FontVariants.centerY(100, 7, 7));
      // taller than the box: centred around it rather than clamped
      assertEquals(99, FontVariants.centerY(100, 5, 7));
   }
}
