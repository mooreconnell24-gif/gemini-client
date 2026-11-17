package gemini.mods;

/**
 * A decoder for lossless WebP (the "VP8L" bitstream, RFC 9649), the format Modrinth serves its 96 px project icons
 * in. Minecraft's own image reader only takes PNG, so without this most icons could not be shown. Lossy WebP ("VP8 ")
 * is not supported: {@link #decode} returns null for it and the caller shows a plain placeholder.
 *
 * <p>Pure Java, no game classes; checked pixel for pixel against a reference decoder in the unit tests.
 */
public final class WebpLossless {
   /** Largest width or height accepted; icons are 96 px, anything far larger is not an icon. */
   public static final int MAX_SIZE = 2048;
   private static final int NUM_LITERAL = 256;
   private static final int NUM_LENGTH_CODES = 24;
   private static final int NUM_DISTANCE_CODES = 40;
   private static final int CODE_LENGTH_CODES = 19;
   private static final int MAX_CODE_LENGTH = 15;
   private static final int[] CODE_LENGTH_ORDER = {17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
   /** (dx, dy) pairs for the first 120 distance codes, in order. */
   private static final int[] DISTANCE_MAP = {
      0, 1, 1, 0, 1, 1, -1, 1, 0, 2, 2, 0, 1, 2,
      -1, 2, 2, 1, -2, 1, 2, 2, -2, 2, 0, 3, 3, 0,
      1, 3, -1, 3, 3, 1, -3, 1, 2, 3, -2, 3, 3, 2,
      -3, 2, 0, 4, 4, 0, 1, 4, -1, 4, 4, 1, -4, 1,
      3, 3, -3, 3, 2, 4, -2, 4, 4, 2, -4, 2, 0, 5,
      3, 4, -3, 4, 4, 3, -4, 3, 5, 0, 1, 5, -1, 5,
      5, 1, -5, 1, 2, 5, -2, 5, 5, 2, -5, 2, 4, 4,
      -4, 4, 0, 6, 6, 0, 1, 6, -1, 6, 6, 1, -6, 1,
      3, 5, -3, 5, 5, 3, -5, 3, 2, 6, -2, 6, 6, 2,
      -6, 2, 4, 5, -4, 5, 5, 4, -5, 4, 3, 6, -3, 6,
      6, 3, -6, 3, 0, 7, 7, 0, 1, 7, -1, 7, 5, 5,
      -5, 5, 7, 1, -7, 1, 4, 6, -4, 6, 6, 4, -6, 4,
      2, 7, -2, 7, 7, 2, -7, 2, 3, 7, -3, 7, 7, 3,
      -7, 3, 5, 6, -5, 6, 6, 5, -6, 5, 8, 0, 4, 7,
      -4, 7, 7, 4, -7, 4, 8, 1, 8, 2, 6, 6, -6, 6,
      8, 3, 5, 7, -5, 7, 7, 5, -7, 5, 8, 4, 6, 7,
      -6, 7, 7, 6, -7, 6, 8, 5, 7, 7, -7, 7, 8, 6,
      8, 7
   };
   private final byte[] data;
   private final int end;
   private int pos;
   private long bitBuffer;
   private int bitCount;

   /** A decoded image: {@code argb} holds width*height pixels row by row as 0xAARRGGBB. */
   public record Image(int width, int height, int[] argb) {
   }

   private WebpLossless(byte[] data, int start, int end) {
      this.data = data;
      this.pos = start;
      this.end = end;
   }

   /** Whether {@code data} is a RIFF/WEBP file at all (lossy or lossless). */
   public static boolean isWebp(byte[] data) {
      return data != null && data.length >= 12 && tag(data, 0).equals("RIFF") && tag(data, 8).equals("WEBP");
   }

   /** Decodes a lossless WebP file; null when it is not one (lossy, animated, or damaged). */
   public static Image decode(byte[] file) {
      if (!isWebp(file)) {
         return null;
      }

      int offset = 12;
      while (offset + 8 <= file.length) {
         String tag = tag(file, offset);
         long size = (file[offset + 4] & 0xFFL) | (file[offset + 5] & 0xFFL) << 8 | (file[offset + 6] & 0xFFL) << 16 | (file[offset + 7] & 0xFFL) << 24;
         int body = offset + 8;
         if (size > file.length - body) {
            return null;
         }

         if (tag.equals("VP8L")) {
            try {
               return new WebpLossless(file, body, body + (int)size).decodeStream();
            } catch (RuntimeException e) {
               return null; // damaged stream: show the placeholder instead
            }
         }

         if (tag.equals("VP8 ") || tag.equals("ANMF")) {
            return null;
         }

         offset = body + (int)size + (int)(size & 1L);
      }

      return null;
   }

   private static String tag(byte[] data, int offset) {
      return new String(data, offset, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
   }

   // ---- bit reader -------------------------------------------------------------------------------------------

   private int bits(int n) {
      if (n == 0) {
         return 0;
      }

      while (this.bitCount < n) {
         if (this.pos >= this.end) {
            throw new IllegalStateException("truncated");
         }

         this.bitBuffer |= (long)(this.data[this.pos++] & 0xFF) << this.bitCount;
         this.bitCount += 8;
      }

      int value = (int)(this.bitBuffer & ((1L << n) - 1L));
      this.bitBuffer >>>= n;
      this.bitCount -= n;
      return value;
   }

   // ---- stream -----------------------------------------------------------------------------------------------

   private Image decodeStream() {
      if (this.bits(8) != 0x2F) {
         throw new IllegalStateException("signature");
      }

      int width = this.bits(14) + 1;
      int height = this.bits(14) + 1;
      this.bits(1); // alpha hint: the pixels carry their alpha either way
      if (this.bits(3) != 0) {
         throw new IllegalStateException("version");
      }

      if (width > MAX_SIZE || height > MAX_SIZE) {
         throw new IllegalStateException("too large");
      }

      // transforms, in the order they are read; applied backwards afterwards
      int[] types = new int[4];
      int[] transformBits = new int[4];
      int[] transformWidth = new int[4];
      int[][] transformData = new int[4][];
      int transforms = 0;
      int xsize = width;
      boolean[] seen = new boolean[4];
      while (this.bits(1) == 1) {
         int type = this.bits(2);
         if (seen[type] || transforms == 4) {
            throw new IllegalStateException("repeated transform");
         }

         seen[type] = true;
         types[transforms] = type;
         transformWidth[transforms] = xsize;
         switch (type) {
            case 0, 1 -> {
               int sizeBits = this.bits(3) + 2;
               transformBits[transforms] = sizeBits;
               transformData[transforms] = this.decodeImage(divRoundUp(xsize, 1 << sizeBits), divRoundUp(height, 1 << sizeBits), false);
            }
            case 3 -> {
               int tableSize = this.bits(8) + 1;
               int[] table = this.decodeImage(tableSize, 1, false);
               for (int i = 1; i < tableSize; i++) {
                  table[i] = addPixels(table[i], table[i - 1]);
               }

               int widthBits = tableSize <= 2 ? 3 : (tableSize <= 4 ? 2 : (tableSize <= 16 ? 1 : 0));
               transformBits[transforms] = widthBits;
               transformData[transforms] = table;
               xsize = divRoundUp(xsize, 1 << widthBits);
            }
            default -> {
            }
         }

         transforms++;
      }

      int[] pixels = this.decodeImage(xsize, height, true);
      for (int t = transforms - 1; t >= 0; t--) {
         int w = transformWidth[t];
         switch (types[t]) {
            case 0 -> inversePredictor(pixels, w, height, transformBits[t], transformData[t]);
            case 1 -> inverseColor(pixels, w, height, transformBits[t], transformData[t]);
            case 2 -> inverseSubtractGreen(pixels);
            default -> pixels = inverseIndexing(pixels, w, height, transformBits[t], transformData[t]);
         }
      }

      if (pixels.length != width * height) {
         throw new IllegalStateException("size");
      }

      return new Image(width, height, pixels);
   }

   private static int divRoundUp(int value, int by) {
      return (value + by - 1) / by;
   }

   /** One entropy-coded image; only the main image may use a meta prefix (entropy) image. */
   private int[] decodeImage(int xsize, int ysize, boolean main) {
      int cacheBits = 0;
      if (this.bits(1) == 1) {
         cacheBits = this.bits(4);
         if (cacheBits < 1 || cacheBits > 11) {
            throw new IllegalStateException("cache bits");
         }
      }

      int prefixBits = 0;
      int[] entropy = null;
      int entropyWidth = 0;
      int groups = 1;
      if (main && this.bits(1) == 1) {
         prefixBits = this.bits(3) + 2;
         entropyWidth = divRoundUp(xsize, 1 << prefixBits);
         entropy = this.decodeImage(entropyWidth, divRoundUp(ysize, 1 << prefixBits), false);
         for (int value : entropy) {
            groups = Math.max(groups, ((value >> 8) & 0xFFFF) + 1);
         }
      }

      int cacheSize = cacheBits == 0 ? 0 : 1 << cacheBits;
      Code[][] codes = new Code[groups][];
      for (int g = 0; g < groups; g++) {
         codes[g] = new Code[]{
            this.readCode(NUM_LITERAL + NUM_LENGTH_CODES + cacheSize),
            this.readCode(NUM_LITERAL),
            this.readCode(NUM_LITERAL),
            this.readCode(NUM_LITERAL),
            this.readCode(NUM_DISTANCE_CODES)
         };
      }

      int total = xsize * ysize;
      int[] out = new int[total];
      int[] cache = cacheSize == 0 ? null : new int[cacheSize];
      int cached = 0;
      int i = 0;
      while (i < total) {
         Code[] group = codes[0];
         if (entropy != null) {
            int x = i % xsize;
            int y = i / xsize;
            group = codes[(entropy[(y >> prefixBits) * entropyWidth + (x >> prefixBits)] >> 8) & 0xFFFF];
         }

         int symbol = group[0].read(this);
         if (symbol < NUM_LITERAL) {
            int red = group[1].read(this);
            int blue = group[2].read(this);
            int alpha = group[3].read(this);
            out[i++] = alpha << 24 | red << 16 | symbol << 8 | blue;
         } else if (symbol < NUM_LITERAL + NUM_LENGTH_CODES) {
            int length = this.prefixValue(symbol - NUM_LITERAL);
            int distanceSymbol = group[4].read(this);
            int distance = planeDistance(xsize, this.prefixValue(distanceSymbol));
            if (distance > i || i + length > total) {
               throw new IllegalStateException("bad copy");
            }

            for (int k = 0; k < length; k++) {
               out[i] = out[i - distance];
               i++;
            }
         } else {
            if (cache == null) {
               throw new IllegalStateException("no cache");
            }

            // bring the cache up to date before reading from it
            while (cached < i) {
               cache[hash(out[cached], cacheBits)] = out[cached];
               cached++;
            }

            out[i++] = cache[symbol - NUM_LITERAL - NUM_LENGTH_CODES];
         }

         if (cache != null) {
            while (cached < i) {
               cache[hash(out[cached], cacheBits)] = out[cached];
               cached++;
            }
         }
      }

      return out;
   }

   private static int hash(int argb, int bits) {
      return (0x1E35A7BD * argb) >>> (32 - bits);
   }

   private int prefixValue(int symbol) {
      if (symbol < 4) {
         return symbol + 1;
      }

      int extra = (symbol - 2) >> 1;
      int offset = (2 + (symbol & 1)) << extra;
      return offset + this.bits(extra) + 1;
   }

   private static int planeDistance(int xsize, int code) {
      if (code > 120) {
         return code - 120;
      }

      int dx = DISTANCE_MAP[(code - 1) * 2];
      int dy = DISTANCE_MAP[(code - 1) * 2 + 1];
      int distance = dx + dy * xsize;
      return Math.max(1, distance);
   }

   // ---- prefix codes -----------------------------------------------------------------------------------------

   private Code readCode(int alphabet) {
      int[] lengths = new int[alphabet];
      if (this.bits(1) == 1) {
         int count = this.bits(1) + 1;
         int first = this.bits(this.bits(1) == 1 ? 8 : 1);
         if (first >= alphabet) {
            throw new IllegalStateException("symbol");
         }

         lengths[first] = 1;
         if (count == 2) {
            int second = this.bits(8);
            if (second >= alphabet) {
               throw new IllegalStateException("symbol");
            }

            lengths[second] = 1;
         }

         return new Code(lengths);
      }

      int[] codeLengthLengths = new int[CODE_LENGTH_CODES];
      int count = this.bits(4) + 4;
      for (int i = 0; i < count; i++) {
         codeLengthLengths[CODE_LENGTH_ORDER[i]] = this.bits(3);
      }

      Code lengthCode = new Code(codeLengthLengths);
      int maxSymbol = alphabet;
      if (this.bits(1) == 1) {
         int lengthBits = 2 + 2 * this.bits(3);
         maxSymbol = 2 + this.bits(lengthBits);
         if (maxSymbol > alphabet) {
            throw new IllegalStateException("max symbol");
         }
      }

      int previous = 8;
      int symbol = 0;
      while (symbol < alphabet) {
         if (maxSymbol-- == 0) {
            break;
         }

         int code = lengthCode.read(this);
         if (code < 16) {
            lengths[symbol++] = code;
            if (code != 0) {
               previous = code;
            }
         } else {
            int repeat;
            int value;
            if (code == 16) {
               repeat = 3 + this.bits(2);
               value = previous;
            } else if (code == 17) {
               repeat = 3 + this.bits(3);
               value = 0;
            } else {
               repeat = 11 + this.bits(7);
               value = 0;
            }

            if (symbol + repeat > alphabet) {
               throw new IllegalStateException("repeat");
            }

            for (int k = 0; k < repeat; k++) {
               lengths[symbol++] = value;
            }
         }
      }

      return new Code(lengths);
   }

   /** A canonical prefix code, decoded bit by bit (the images are small, so no lookup tables). */
   private static final class Code {
      private final int[] counts = new int[MAX_CODE_LENGTH + 1];
      private final int[] symbols;
      /** The only symbol when exactly one has a length: it takes no bits at all. */
      private final int single;

      Code(int[] lengths) {
         int used = 0;
         int last = -1;
         for (int s = 0; s < lengths.length; s++) {
            if (lengths[s] > MAX_CODE_LENGTH) {
               throw new IllegalStateException("code length");
            }

            if (lengths[s] > 0) {
               this.counts[lengths[s]]++;
               used++;
               last = s;
            }
         }

         if (used == 0) {
            throw new IllegalStateException("empty code");
         }

         this.single = used == 1 ? last : -1;
         int[] offsets = new int[MAX_CODE_LENGTH + 2];
         for (int len = 1; len <= MAX_CODE_LENGTH; len++) {
            offsets[len + 1] = offsets[len] + this.counts[len];
         }

         this.symbols = new int[used];
         for (int s = 0; s < lengths.length; s++) {
            if (lengths[s] > 0) {
               this.symbols[offsets[lengths[s]]++] = s;
            }
         }
      }

      int read(WebpLossless in) {
         if (this.single >= 0) {
            return this.single;
         }

         int code = 0;
         int first = 0;
         int index = 0;
         for (int len = 1; len <= MAX_CODE_LENGTH; len++) {
            code |= in.bits(1);
            int count = this.counts[len];
            if (code - first < count) {
               return this.symbols[index + code - first];
            }

            index += count;
            first += count;
            first <<= 1;
            code <<= 1;
         }

         throw new IllegalStateException("bad code");
      }
   }

   // ---- inverse transforms -----------------------------------------------------------------------------------

   private static int addPixels(int a, int b) {
      int alpha = ((a >>> 24) + (b >>> 24)) & 0xFF;
      int red = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) & 0xFF;
      int green = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) & 0xFF;
      int blue = ((a & 0xFF) + (b & 0xFF)) & 0xFF;
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   private static int average2(int a, int b) {
      return (int)(((a & 0xFEFEFEFEL) >>> 1) + ((b & 0xFEFEFEFEL) >>> 1) + (a & b & 0x01010101L));
   }

   private static int channel(int pixel, int shift) {
      return pixel >>> shift & 0xFF;
   }

   private static int clamp(int value) {
      return value < 0 ? 0 : (value > 255 ? 255 : value);
   }

   private static int select(int left, int top, int topLeft) {
      int pl = 0;
      int pt = 0;
      for (int shift = 0; shift < 32; shift += 8) {
         int estimate = channel(left, shift) + channel(top, shift) - channel(topLeft, shift);
         pl += Math.abs(estimate - channel(left, shift));
         pt += Math.abs(estimate - channel(top, shift));
      }

      return pl < pt ? left : top;
   }

   private static int clampAddSubtractFull(int a, int b, int c) {
      int out = 0;
      for (int shift = 0; shift < 32; shift += 8) {
         out |= clamp(channel(a, shift) + channel(b, shift) - channel(c, shift)) << shift;
      }

      return out;
   }

   private static int clampAddSubtractHalf(int a, int b) {
      int out = 0;
      for (int shift = 0; shift < 32; shift += 8) {
         int ca = channel(a, shift);
         out |= clamp(ca + (ca - channel(b, shift)) / 2) << shift;
      }

      return out;
   }

   private static void inversePredictor(int[] p, int width, int height, int sizeBits, int[] modes) {
      int tilesPerRow = divRoundUp(width, 1 << sizeBits);
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int i = y * width + x;
            int prediction;
            if (y == 0) {
               prediction = x == 0 ? 0xFF000000 : p[i - 1];
            } else if (x == 0) {
               prediction = p[i - width];
            } else {
               int mode = modes[(y >> sizeBits) * tilesPerRow + (x >> sizeBits)] >> 8 & 0xF;
               int left = p[i - 1];
               int top = p[i - width];
               int topLeft = p[i - width - 1];
               int topRight = p[i - width + 1];
               prediction = switch (mode) {
                  case 0 -> 0xFF000000;
                  case 1 -> left;
                  case 2 -> top;
                  case 3 -> topRight;
                  case 4 -> topLeft;
                  case 5 -> average2(average2(left, topRight), top);
                  case 6 -> average2(left, topLeft);
                  case 7 -> average2(left, top);
                  case 8 -> average2(topLeft, top);
                  case 9 -> average2(top, topRight);
                  case 10 -> average2(average2(left, topLeft), average2(top, topRight));
                  case 11 -> select(left, top, topLeft);
                  case 12 -> clampAddSubtractFull(left, top, topLeft);
                  case 13 -> clampAddSubtractHalf(average2(left, top), topLeft);
                  default -> 0xFF000000;
               };
            }

            p[i] = addPixels(p[i], prediction);
         }
      }
   }

   private static int colorDelta(int transform, int color) {
      return ((byte)transform * (byte)color) >> 5;
   }

   private static void inverseColor(int[] p, int width, int height, int sizeBits, int[] elements) {
      int tilesPerRow = divRoundUp(width, 1 << sizeBits);
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int element = elements[(y >> sizeBits) * tilesPerRow + (x >> sizeBits)];
            int greenToRed = element & 0xFF;
            int greenToBlue = element >> 8 & 0xFF;
            int redToBlue = element >> 16 & 0xFF;
            int i = y * width + x;
            int argb = p[i];
            int green = argb >> 8 & 0xFF;
            int red = (argb >> 16 & 0xFF) + colorDelta(greenToRed, green) & 0xFF;
            int blue = (argb & 0xFF) + colorDelta(greenToBlue, green);
            blue = blue + colorDelta(redToBlue, red) & 0xFF;
            p[i] = argb & 0xFF00FF00 | red << 16 | blue;
         }
      }
   }

   private static void inverseSubtractGreen(int[] p) {
      for (int i = 0; i < p.length; i++) {
         int argb = p[i];
         int green = argb >> 8 & 0xFF;
         int red = (argb >> 16 & 0xFF) + green & 0xFF;
         int blue = (argb & 0xFF) + green & 0xFF;
         p[i] = argb & 0xFF00FF00 | red << 16 | blue;
      }
   }

   private static int[] inverseIndexing(int[] packed, int width, int height, int widthBits, int[] table) {
      int packedWidth = divRoundUp(width, 1 << widthBits);
      int bitsPerPixel = 8 >> widthBits;
      int mask = (1 << bitsPerPixel) - 1;
      int[] out = new int[width * height];
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int value = packed[y * packedWidth + (x >> widthBits)] >> 8 & 0xFF;
            int index = value >> ((x & (1 << widthBits) - 1) * bitsPerPixel) & mask;
            out[y * width + x] = index < table.length ? table[index] : 0;
         }
      }

      return out;
   }
}
