package gemini.ui;

/**
 * Where everything on {@link GeminiTitleScreen} goes for a given GUI size. Pure arithmetic, so it can be tested
 * without a running game: the navigation buttons use one column when the screen is tall enough and two columns
 * of five otherwise, and always stay between the header and the footer row.
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
   public static final int BUTTONS = 10;
   public static final int STATUS_HEIGHT = 16;
   public static final int FOOTER_HEIGHT = 16;
   private static final int COLUMN_GAP = 6;

   public static TitleLayout compute(int width, int height) {
      boolean compact = height < 300;
      int logoSize = compact ? 28 : 36;
      int headerY = compact ? 8 : Math.max(14, height / 9);
      int statusY = height - STATUS_HEIGHT;
      int footerY = statusY - 4 - FOOTER_HEIGHT;
      int top = headerY + logoSize + (compact ? 8 : 16);
      int bottom = footerY - 6;
      int available = Math.max(0, bottom - top);
      int buttonHeight = Theme.BUTTON_HEIGHT;
      int gap = Theme.SPACE_S;
      int columns = 1;
      int rows = BUTTONS;
      if (available < rows * buttonHeight + (rows - 1) * gap) {
         columns = 2;
         rows = BUTTONS / 2;
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
      return BUTTONS / this.columns;
   }

   /** Left edge of navigation button {@code index}; buttons fill the first column top to bottom, then the second. */
   public int buttonX(int index) {
      return this.startX + (index / this.rows()) * (this.buttonWidth + COLUMN_GAP);
   }

   public int buttonY(int index) {
      return this.startY + (index % this.rows()) * (this.buttonHeight + this.gap);
   }

   /** Bottom edge of the lowest navigation button. */
   public int buttonsBottom() {
      return this.buttonY(this.rows() - 1) + this.buttonHeight;
   }
}
