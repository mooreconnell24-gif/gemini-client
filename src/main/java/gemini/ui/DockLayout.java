package gemini.ui;

/**
 * Placement of the small Gemini group on the pause menu. Pure arithmetic so it can be tested without a running game.
 *
 * <p>The vanilla pause menu is a 204 pixel wide column in the middle of the screen with its title at y 40. The
 * group is a short stack in the bottom-left corner when that leaves the vanilla column untouched, and otherwise a
 * single row along the very top, above the title, with short labels if the full ones do not fit. The panel
 * fields are the bounding box of the group (nothing is drawn behind it).
 */
public record DockLayout(boolean side, boolean shortLabels, int panelX, int panelY, int panelWidth, int panelHeight, int[] x, int[] y, int[] w, int buttonHeight) {
   /** Half the width of the vanilla pause column plus its cell padding. */
   public static final int VANILLA_HALF_WIDTH = 106;
   /** The vanilla "Game Menu" title sits at this y; a top row has to end above it. */
   public static final int VANILLA_TITLE_Y = 40;
   public static final int BUTTON_HEIGHT = 16;
   private static final int EDGE = 6;
   private static final int GAP = 3;

   /**
    * @param fullWidths text width of each full label
    * @param shortWidths text width of each short label
    */
   public static DockLayout compute(int screenWidth, int screenHeight, int[] fullWidths, int[] shortWidths) {
      int count = fullWidths.length;
      int widest = 0;
      for (int width : fullWidths) {
         widest = Math.max(widest, width);
      }

      int sideButton = Math.max(72, Math.min(120, widest + 16));
      int sideHeight = count * BUTTON_HEIGHT + (count - 1) * GAP;
      int[] x = new int[count];
      int[] y = new int[count];
      int[] w = new int[count];
      if (EDGE + sideButton + EDGE <= screenWidth / 2 - VANILLA_HALF_WIDTH && sideHeight + EDGE * 2 <= screenHeight) {
         int top = screenHeight - EDGE - sideHeight;
         for (int i = 0; i < count; i++) {
            x[i] = EDGE;
            y[i] = top + i * (BUTTON_HEIGHT + GAP);
            w[i] = sideButton;
         }

         return new DockLayout(true, false, EDGE, top, sideButton, sideHeight, x, y, w, BUTTON_HEIGHT);
      }

      int available = Math.max(count, screenWidth - EDGE * 2 - (count - 1) * GAP);
      int fullTotal = 0;
      for (int width : fullWidths) {
         fullTotal += width + 12;
      }

      boolean shortLabels = fullTotal > available;
      int total = 0;
      if (!shortLabels) {
         for (int i = 0; i < count; i++) {
            w[i] = fullWidths[i] + 12;
            total += w[i];
         }
      } else {
         int shortTotal = 0;
         for (int width : shortWidths) {
            shortTotal += width + 10;
         }

         for (int i = 0; i < count; i++) {
            // Natural widths when they fit, otherwise an equal share of the row.
            w[i] = shortTotal <= available ? shortWidths[i] + 10 : available / count;
            total += w[i];
         }
      }

      int rowWidth = total + (count - 1) * GAP;
      int panelX = (screenWidth - rowWidth) / 2;
      int panelY = 8;
      int cursor = panelX;
      for (int i = 0; i < count; i++) {
         x[i] = cursor;
         y[i] = panelY;
         cursor += w[i] + GAP;
      }

      return new DockLayout(false, shortLabels, panelX, panelY, rowWidth, BUTTON_HEIGHT, x, y, w, BUTTON_HEIGHT);
   }
}
