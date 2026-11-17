package gemini.ui.clickgui;

/**
 * Geometry of the theme switcher's popover: one round swatch per preset in rows of {@link #COLUMNS}, with a line
 * under them for the name of the theme under the mouse. Pure arithmetic, unit tested.
 */
public final class ThemeSwatches {
   public static final int COLUMNS = 5;
   /** Side of one swatch cell (the dot is inset inside it). */
   public static final int CELL = 22;
   public static final int PAD = 6;
   /** Height of the name line under the swatches. */
   public static final int LABEL = 16;

   private ThemeSwatches() {
   }

   public static int rows(int count) {
      return (Math.max(0, count) + COLUMNS - 1) / COLUMNS;
   }

   public static int width() {
      return COLUMNS * CELL + PAD * 2;
   }

   public static int height(int count) {
      return rows(count) * CELL + PAD * 2 + LABEL;
   }

   /** Left edge of swatch {@code index}, relative to the popover. */
   public static int cellX(int index) {
      return PAD + index % COLUMNS * CELL;
   }

   /** Top edge of swatch {@code index}, relative to the popover. */
   public static int cellY(int index) {
      return PAD + index / COLUMNS * CELL;
   }

   /** The swatch under a point given relative to the popover, or -1. */
   public static int indexAt(int x, int y, int count) {
      int column = Math.floorDiv(x - PAD, CELL);
      int row = Math.floorDiv(y - PAD, CELL);
      if (column < 0 || column >= COLUMNS || row < 0) {
         return -1;
      }

      int index = row * COLUMNS + column;
      return index < count ? index : -1;
   }
}
