package gemini.ui;

/**
 * Where everything on {@link GeminiTitleScreen} goes for a given GUI size. Pure arithmetic, so it can be tested
 * without a running game.
 *
 * <p>There are {@link #BUTTONS} main buttons: Singleplayer, Multiplayer, Realms, Gemini, Options and Quit. On a
 * screen tall enough they form one column, with Options and Quit sharing the last row as on the vanilla title
 * screen; otherwise two columns of three. Under them sits one row of small text links (quick search, guide,
 * presets), and everything stays between the header and the footer row.
 */
public record TitleLayout(
   int width,
   int height,
   int logoSize,
   int headerY,
   int columns,
   int buttonWidth,
   int buttonHeight,
   int gap,
   int startX,
   int startY,
   int footerY,
   int statusY
) {
   public static final int BUTTONS = 6;
   public static final int STATUS_HEIGHT = 12;
   public static final int FOOTER_HEIGHT = 14;
   public static final int LINK_HEIGHT = 12;
   /** Space between the buttons and the row of links. */
   public static final int LINK_GAP = 6;
   /** Horizontal space between two links. */
   public static final int LINK_SPACING = 14;
   private static final int COLUMN_GAP = 6;
   private static final int ROWS_ONE_COLUMN = 5;
   private static final int ROWS_TWO_COLUMNS = 3;

   public static TitleLayout compute(int width, int height) {
      boolean compact = height < 300;
      int logoSize = compact ? 24 : 32;
      int headerY = compact ? 8 : Math.max(14, height / 8);
      int statusY = height - STATUS_HEIGHT;
      int footerY = statusY - 2 - FOOTER_HEIGHT;
      int top = headerY + logoSize + (compact ? 10 : 18);
      int bottom = footerY - 6;
      int available = Math.max(0, bottom - top - LINK_GAP - LINK_HEIGHT);
      int buttonHeight = Theme.BUTTON_HEIGHT;
      int gap = Theme.SPACE_S;
      int columns = 1;
      int rows = ROWS_ONE_COLUMN;
      if (available < rows * buttonHeight + (rows - 1) * gap) {
         columns = 2;
         rows = ROWS_TWO_COLUMNS;
         if (available < rows * buttonHeight + (rows - 1) * gap) {
            // Very short windows: squeeze the rows rather than run into the footer.
            gap = Theme.SPACE_XS;
            buttonHeight = Math.max(10, (available - (rows - 1) * gap) / rows);
         }
      }

      int buttonWidth = columns == 1
         ? Math.max(60, Math.min(200, width - 40))
         : Math.max(40, Math.min(150, (width - 24 - COLUMN_GAP) / 2));
      int blockWidth = columns * buttonWidth + (columns - 1) * COLUMN_GAP;
      int blockHeight = rows * buttonHeight + (rows - 1) * gap;
      int startX = (width - blockWidth) / 2;
      // A third of the spare room above the block keeps it visually tied to the header.
      int startY = top + Math.max(0, available - blockHeight) / 3;
      return new TitleLayout(width, height, logoSize, headerY, columns, buttonWidth, buttonHeight, gap, startX, startY, footerY, statusY);
   }

   public int rows() {
      return this.columns == 1 ? ROWS_ONE_COLUMN : ROWS_TWO_COLUMNS;
   }

   /** Whether main button {@code index} shares its row with its neighbour (Options and Quit in the one-column layout). */
   private boolean halfWidth(int index) {
      return this.columns == 1 && index >= 4;
   }

   /** Left edge of main button {@code index}. */
   public int buttonX(int index) {
      if (this.columns == 1) {
         return index == 5 ? this.startX + this.buttonW(4) + COLUMN_GAP : this.startX;
      }

      return this.startX + (index / ROWS_TWO_COLUMNS) * (this.buttonWidth + COLUMN_GAP);
   }

   public int buttonY(int index) {
      int row = this.columns == 1 ? Math.min(index, 4) : index % ROWS_TWO_COLUMNS;
      return this.startY + row * (this.buttonHeight + this.gap);
   }

   /** Width of main button {@code index}. */
   public int buttonW(int index) {
      if (!this.halfWidth(index)) {
         return this.buttonWidth;
      }

      int left = (this.buttonWidth - COLUMN_GAP) / 2;
      return index == 4 ? left : this.buttonWidth - COLUMN_GAP - left;
   }

   /** Bottom edge of the lowest main button. */
   public int buttonsBottom() {
      return this.startY + this.rows() * this.buttonHeight + (this.rows() - 1) * this.gap;
   }

   /** Top of the row of text links under the buttons. */
   public int linksY() {
      return this.buttonsBottom() + LINK_GAP;
   }

   /**
    * Left edges of the text links, given each link's width: one row centred under the buttons with
    * {@link #LINK_SPACING} between links.
    */
   public int[] linkX(int[] widths) {
      int total = 0;
      for (int width : widths) {
         total += width;
      }

      total += Math.max(0, widths.length - 1) * LINK_SPACING;
      int[] x = new int[widths.length];
      int cursor = (this.width - total) / 2;
      for (int i = 0; i < widths.length; i++) {
         x[i] = cursor;
         cursor += widths[i] + LINK_SPACING;
      }

      return x;
   }
}
