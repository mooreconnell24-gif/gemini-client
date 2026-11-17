package gemini.mods;

/** Small pixel helpers for project icons. Pure Java, ARGB ints row by row. */
public final class Images {
   private Images() {
   }

   /**
    * Scales {@code argb} ({@code width} x {@code height}) to a {@code size} x {@code size} square with an area
    * (box) filter, keeping the aspect ratio and centring it on transparent pixels. Colours are averaged weighted by
    * alpha so transparent edges do not turn dark.
    */
   public static int[] fitSquare(int[] argb, int width, int height, int size) {
      int[] out = new int[size * size];
      if (width <= 0 || height <= 0 || argb.length < width * height) {
         return out;
      }

      float scale = Math.min((float)size / width, (float)size / height);
      int dw = Math.max(1, Math.round(width * scale));
      int dh = Math.max(1, Math.round(height * scale));
      int ox = (size - dw) / 2;
      int oy = (size - dh) / 2;
      for (int dy = 0; dy < dh; dy++) {
         int sy0 = dy * height / dh;
         int sy1 = Math.max(sy0 + 1, (dy + 1) * height / dh);
         for (int dx = 0; dx < dw; dx++) {
            int sx0 = dx * width / dw;
            int sx1 = Math.max(sx0 + 1, (dx + 1) * width / dw);
            long a = 0;
            long r = 0;
            long g = 0;
            long b = 0;
            int n = 0;
            for (int sy = sy0; sy < sy1; sy++) {
               for (int sx = sx0; sx < sx1; sx++) {
                  int p = argb[sy * width + sx];
                  int alpha = p >>> 24;
                  a += alpha;
                  r += (long)(p >> 16 & 0xFF) * alpha;
                  g += (long)(p >> 8 & 0xFF) * alpha;
                  b += (long)(p & 0xFF) * alpha;
                  n++;
               }
            }

            int pixel = 0;
            if (a > 0) {
               pixel = (int)(a / n) << 24 | (int)(r / a) << 16 | (int)(g / a) << 8 | (int)(b / a);
            }

            out[(oy + dy) * size + ox + dx] = pixel;
         }
      }

      return out;
   }
}
