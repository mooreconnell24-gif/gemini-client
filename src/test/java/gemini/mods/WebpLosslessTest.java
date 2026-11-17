package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class WebpLosslessTest {
   /**
    * Real Modrinth icons (96 px lossless WebP, with and without alpha). {@code expected.txt} holds each one's size
    * and the SHA-256 of its RGBA bytes as decoded by libwebp (through Pillow) when the fixtures were recorded.
    */
   @Test
   void decodesModrinthIconsExactly() throws Exception {
      String expected = new String(Fixtures.bytes("webp/expected.txt"), StandardCharsets.UTF_8);
      int checked = 0;
      for (String line : expected.strip().split("\n")) {
         String[] parts = line.trim().split(" ");
         WebpLossless.Image image = WebpLossless.decode(Fixtures.bytes("webp/" + parts[0]));
         assertNotNull(image, parts[0]);
         assertEquals(Integer.parseInt(parts[1]), image.width(), parts[0]);
         assertEquals(Integer.parseInt(parts[2]), image.height(), parts[0]);
         byte[] rgba = new byte[image.argb().length * 4];
         for (int i = 0; i < image.argb().length; i++) {
            int p = image.argb()[i];
            rgba[i * 4] = (byte)(p >> 16);
            rgba[i * 4 + 1] = (byte)(p >> 8);
            rgba[i * 4 + 2] = (byte)p;
            rgba[i * 4 + 3] = (byte)(p >>> 24);
         }

         String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));
         assertEquals(parts[3], hash, parts[0] + " pixels");
         checked++;
      }

      assertEquals(6, checked);
   }

   @Test
   void rejectsWhatItCannotDecode() {
      assertNull(WebpLossless.decode(new byte[0]));
      assertNull(WebpLossless.decode("not an image at all".getBytes(StandardCharsets.UTF_8)));
      byte[] lossy = "RIFF\u0010\u0000\u0000\u0000WEBPVP8 \u0004\u0000\u0000\u0000abcd".getBytes(StandardCharsets.ISO_8859_1);
      assertTrue(WebpLossless.isWebp(lossy));
      assertNull(WebpLossless.decode(lossy));
      byte[] good = Fixtures.bytes("webp/icon4.webp");
      byte[] truncated = java.util.Arrays.copyOf(good, good.length / 2);
      assertNull(WebpLossless.decode(truncated));
   }

   @Test
   void downscalesToASquare() {
      WebpLossless.Image image = WebpLossless.decode(Fixtures.bytes("webp/icon2.webp"));
      int[] small = Images.fitSquare(image.argb(), image.width(), image.height(), 32);
      assertEquals(32 * 32, small.length);
      int[] wide = Images.fitSquare(new int[]{0xFFFF0000, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000}, 4, 1, 4);
      // a 4x1 strip lands in the middle row of a 4x4 square, the rest transparent
      assertEquals(0, wide[0]);
      assertEquals(0xFFFF0000, wide[4 + 1]);
      assertEquals(0, wide[8 + 1]);
   }
}
