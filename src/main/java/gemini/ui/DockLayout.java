package gemini.ui;

/**
 * Placement of the pause-menu quick-action dock. Pure arithmetic so it can be tested without a running game.
 *
 * <p>The vanilla pause menu is a 204 pixel wide column in the middle of the screen with its title at y 40. The
 * dock is a labelled column down the left edge when that leaves the vanilla column untouched, and otherwise a
 * single row along the very top, above the title, with short labels if the full ones do not fit.
 */
public record DockLayout(boolean side, boolean shortLabels, int panelX, int panelY, int panelWidth, int panelHeight, int[] x, int[] y, int[] w, int buttonHeight) {
   /** Half the width of the vanilla pause column plus its cell padding. */
   public static final int VANILLA_HALF_WIDTH = 106;
   /** The vanilla "Game Menu" title sits at this y; a top dock has to end above it. */
   public static final int VANILLA_TITLE_Y = 40;
   public static final int CAPTION_HEIGHT = 12;
   private static final int EDGE = 6;
   private static final int PAD = 4;

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

      int sideButton = Math.max(88, Math.min(132, widest + 20));
      int sideHeight = 18;
      int sideGap = 3;
      int sidePanelWidth = sideButton + PAD * 2;
      int sidePanelHeight = CAPTION_HEIGHT + count * sideHeight + (count - 1) * sideGap + PAD * 2;
      int[] x = new int[count];
      int[] y = new int[count];
      int[] w = new int[count];
      if (EDGE + sidePanelWidth + EDGE <= screenWidth / 2 - VANILLA_HALF_WIDTH && sidePanelHeight + EDGE * 2 <= screenHeight) {
         int panelY = (screenHeight - sidePanelHeight) / 2;
         for (int i = 0; i < count; i++) {
            x[i] = EDGE + PAD;
            y[i] = panelY + PAD + CAPTION_HEIGHT + i * (sideHeight + sideGap);
            w[i] = sideButton;
         }

         return new DockLayout(true, false, EDGE, panelY, sidePanelWidth, sidePanelHeight, x, y, w, sideHeight);
      }

      int gap = 3;
      int rowHeight = 16;
      int available = Math.max(count, screenWidth - EDGE * 2 - PAD * 2 - (count - 1) * gap);
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

      int rowWidth = total + (count - 1) * gap;
      int panelWidth = rowWidth + PAD * 2;
      int panelX = (screenWidth - panelWidth) / 2;
      int panelY = 3;
      int cursor = panelX + PAD;
      for (int i = 0; i < count; i++) {
         x[i] = cursor;
         y[i] = panelY + PAD;
         cursor += w[i] + gap;
      }

      return new DockLayout(false, shortLabels, panelX, panelY, panelWidth, rowHeight + PAD * 2, x, y, w, rowHeight);
   }
}
