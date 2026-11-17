package gemini.ui.clickgui;

/**
 * Layout of the window menu's module cards: one or two columns that each stack on their own, so a card that grows
 * (its settings opening in place) pushes down only the cards under it. Card {@code i} goes to column
 * {@code i % columns}, which keeps alphabetical order reading left to right, row by row, while every card is closed.
 * Pure arithmetic, unit tested.
 */
public final class CardGrid {
   /** Narrowest card that still fits a name, a description and a switch comfortably. */
   public static final int MIN_CARD = 170;

   private CardGrid() {
   }

   /** How many columns fit in {@code width} with {@code gap} between them (1 or 2). */
   public static int columns(int width, int gap) {
      return width >= MIN_CARD * 2 + gap ? 2 : 1;
   }

   /** Width of one card. */
   public static int cardWidth(int width, int columns, int gap) {
      return (width - (columns - 1) * gap) / Math.max(1, columns);
   }

   public static int column(int index, int columns) {
      return index % Math.max(1, columns);
   }

   /** Left edge of a column, relative to the grid. */
   public static int columnX(int column, int width, int columns, int gap) {
      return column * (cardWidth(width, columns, gap) + gap);
   }

   /**
    * Fills {@code tops} with the top of every card (relative to the grid) given their {@code heights}, and returns
    * the height of the whole grid (the tallest column, without a trailing gap).
    */
   public static int layout(int[] heights, int count, int columns, int gap, int[] tops) {
      int cols = Math.max(1, columns);
      int tallest = 0;
      for (int column = 0; column < cols; column++) {
         int y = 0;
         for (int i = column; i < count; i += cols) {
            tops[i] = y;
            y += heights[i] + gap;
         }

         tallest = Math.max(tallest, y - (y > 0 ? gap : 0));
      }

      return tallest;
   }

   /**
    * The card a keyboard step lands on from {@code index}: {@code dx} moves across columns, {@code dy} down the same
    * column; a step that would leave the grid stays put. From no selection it lands on the first card. Returns -1
    * when there are no cards.
    */
   public static int step(int index, int count, int columns, int dx, int dy) {
      if (count <= 0) {
         return -1;
      }

      if (index < 0 || index >= count) {
         return 0;
      }

      int cols = Math.max(1, columns);
      int next = index + dy * cols;
      if (dx != 0) {
         int column = index % cols + dx;
         next = column < 0 || column >= cols ? index : index + dx;
      }

      return next < 0 || next >= count ? index : next;
   }
}
