package gemini.ui.palette;

import gemini.ui.Theme;

/**
 * Geometry of the quick-search palette for a GUI size. Pure, tested at GUI scales 1 to 4: the card is at most
 * {@link #MAX_WIDTH} wide, sits a little above the middle, and shows as many result rows as fit (never fewer than
 * {@link #MIN_ROWS}, never more than {@link #MAX_ROWS}) while keeping {@link Theme#EDGE} to every screen edge.
 */
public record PaletteLayout(int x, int y, int width, int height, int fieldY, int kindsY, int listY, int rows, int footerY) {
   public static final int MAX_WIDTH = 400;
   public static final int FIELD_HEIGHT = 18;
   public static final int KINDS_HEIGHT = 12;
   public static final int ROW_HEIGHT = Theme.ROW_HEIGHT;
   public static final int FOOTER_HEIGHT = 2 * Theme.LINE + Theme.SPACE_S;
   public static final int MIN_ROWS = 3;
   public static final int MAX_ROWS = 10;

   public static PaletteLayout compute(int screenWidth, int screenHeight) {
      int width = Math.max(140, Math.min(MAX_WIDTH, screenWidth - 2 * Theme.EDGE));
      int chrome = Theme.SPACE_L + FIELD_HEIGHT + Theme.SPACE_M + KINDS_HEIGHT + Theme.SPACE_S + Theme.SPACE_S + FOOTER_HEIGHT + Theme.SPACE_S;
      int room = screenHeight - 2 * Theme.EDGE - chrome;
      int rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, room / ROW_HEIGHT));
      int height = chrome + rows * ROW_HEIGHT;
      int x = (screenWidth - width) / 2;
      // A third of the free space above, two thirds below: the palette reads as "in front", not centred.
      int y = Math.max(Theme.EDGE, Math.min((screenHeight - height) / 3, screenHeight - height - Theme.EDGE));
      if (height > screenHeight - 2 * Theme.EDGE) {
         y = Math.max(0, (screenHeight - height) / 2);
      }

      int fieldY = y + Theme.SPACE_L;
      int kindsY = fieldY + FIELD_HEIGHT + Theme.SPACE_M;
      int listY = kindsY + KINDS_HEIGHT + Theme.SPACE_S;
      int footerY = listY + rows * ROW_HEIGHT + Theme.SPACE_S;
      return new PaletteLayout(x, y, width, height, fieldY, kindsY, listY, rows, footerY);
   }
}
