package gemini.ui;

/**
 * Placement arithmetic shared by the small windowed screens (world backups, notes, the keybind report). Pure, so
 * it is tested without a running game.
 */
public final class ScreenBounds {
   /** Least distance between a window and the edge of the screen. */
   public static final int MARGIN = 8;

   private ScreenBounds() {
   }

   /**
    * A window centred on a {@code width} x {@code height} screen: {@code maxWidth} x {@code maxHeight} when there
    * is room, otherwise the screen less {@link #MARGIN} on each side. Returns x, y, width, height.
    */
   public static int[] window(int width, int height, int maxWidth, int maxHeight) {
      int w = Math.max(1, Math.min(maxWidth, width - 2 * MARGIN));
      int h = Math.max(1, Math.min(maxHeight, height - 2 * MARGIN));
      return new int[]{(width - w) / 2, (height - h) / 2, w, h};
   }

   /** Padding between a window's edge and its content: {@link GlassWindow#PAD}, tighter on a very narrow window. */
   public static int pad(int windowWidth) {
      return windowWidth < 220 ? Theme.SPACE_L : GlassWindow.PAD;
   }

   /** Width of each of {@code count} equal buttons sharing the span {@code left..right} with {@code gap} between them. */
   public static int rowWidth(int left, int right, int count, int gap) {
      int n = Math.max(1, count);
      return Math.max(1, (right - left - (n - 1) * gap) / n);
   }

   /** Left edges of {@code count} equal buttons sharing the span {@code left..right} with {@code gap} between them. */
   public static int[] row(int left, int right, int count, int gap) {
      int n = Math.max(1, count);
      int w = rowWidth(left, right, n, gap);
      int[] x = new int[n];
      for (int i = 0; i < n; i++) {
         x[i] = left + i * (w + gap);
      }

      return x;
   }
}
