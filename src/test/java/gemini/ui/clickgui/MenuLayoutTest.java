package gemini.ui.clickgui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.ui.clickgui.controls.ModeControl;
import org.junit.jupiter.api.Test;

/** The pure layout maths of the reworked menus: the card grid, the theme swatches and the mode control's fit rule. */
class MenuLayoutTest {
   private static final int GAP = 6;

   @Test
   void gridUsesTwoColumnsOnlyWhenBothCardsFit() {
      assertEquals(1, CardGrid.columns(CardGrid.MIN_CARD * 2 + GAP - 1, GAP));
      assertEquals(2, CardGrid.columns(CardGrid.MIN_CARD * 2 + GAP, GAP));
      assertEquals(1, CardGrid.columns(0, GAP));
   }

   @Test
   void cardsFillTheWidthExactlyOrLeaveLessThanAColumnCount() {
      for (int width = 200; width < 700; width += 7) {
         int columns = CardGrid.columns(width, GAP);
         int card = CardGrid.cardWidth(width, columns, GAP);
         int used = card * columns + (columns - 1) * GAP;
         assertTrue(used <= width && width - used < columns, "width " + width);
         assertEquals(used - card, CardGrid.columnX(columns - 1, width, columns, GAP));
      }
   }

   @Test
   void closedCardsReadLeftToRightRowByRow() {
      int[] heights = {36, 36, 36, 36, 36};
      int[] tops = new int[5];
      int height = CardGrid.layout(heights, 5, 2, GAP, tops);
      assertArrayEquals(new int[]{0, 0, 42, 42, 84}, tops);
      assertEquals(120, height, "three rows of 36 with two gaps");
      assertEquals(0, CardGrid.column(4, 2));
      assertEquals(1, CardGrid.column(3, 2));
   }

   @Test
   void anOpenCardPushesDownOnlyItsOwnColumn() {
      int[] heights = {36, 200, 36, 36, 36, 36};
      int[] tops = new int[6];
      int height = CardGrid.layout(heights, 6, 2, GAP, tops);
      assertEquals(42, tops[2], "left column is not moved by the open card on the right");
      assertEquals(84, tops[4]);
      assertEquals(206, tops[3], "right column continues under the open card");
      assertEquals(248, tops[5]);
      assertEquals(284, height, "the taller column sets the content height");
   }

   @Test
   void singleColumnStacksEverything() {
      int[] heights = {10, 20, 30};
      int[] tops = new int[3];
      assertEquals(10 + 20 + 30 + 2 * GAP, CardGrid.layout(heights, 3, 1, GAP, tops));
      assertArrayEquals(new int[]{0, 16, 42}, tops);
      assertEquals(0, CardGrid.layout(heights, 0, 2, GAP, tops), "no cards, no height");
   }

   @Test
   void keyboardStepsStayInsideTheGrid() {
      assertEquals(-1, CardGrid.step(0, 0, 2, 0, 1), "nothing to select");
      assertEquals(0, CardGrid.step(-1, 5, 2, 0, 1), "the first step lands on the first card");
      assertEquals(2, CardGrid.step(0, 5, 2, 0, 1), "down stays in the column");
      assertEquals(1, CardGrid.step(0, 5, 2, 1, 0));
      assertEquals(1, CardGrid.step(1, 5, 2, 1, 0), "no column to the right of the last one");
      assertEquals(0, CardGrid.step(0, 5, 2, -1, 0));
      assertEquals(0, CardGrid.step(0, 5, 2, 0, -1), "already at the top");
      assertEquals(3, CardGrid.step(3, 5, 2, 0, 1), "no card under the last of the right column");
      assertEquals(4, CardGrid.step(4, 5, 2, 1, 0), "the last row has no right-hand card");
      assertEquals(4, CardGrid.step(3, 5, 1, 0, 1));
   }

   @Test
   void themeSwatchesAreAFiveColumnGrid() {
      assertEquals(2, ThemeSwatches.rows(10));
      assertEquals(3, ThemeSwatches.rows(11));
      assertEquals(0, ThemeSwatches.rows(0));
      assertEquals(ThemeSwatches.COLUMNS * ThemeSwatches.CELL + 2 * ThemeSwatches.PAD, ThemeSwatches.width());
      assertEquals(2 * ThemeSwatches.CELL + 2 * ThemeSwatches.PAD + ThemeSwatches.LABEL, ThemeSwatches.height(10));
      assertEquals(ThemeSwatches.cellX(0), ThemeSwatches.cellX(5), "rows line up");
      assertEquals(ThemeSwatches.cellY(0) + ThemeSwatches.CELL, ThemeSwatches.cellY(7));
   }

   @Test
   void everySwatchIsHitInsideItsOwnCellAndNowhereElse() {
      int count = 8;
      for (int i = 0; i < count; i++) {
         int x = ThemeSwatches.cellX(i);
         int y = ThemeSwatches.cellY(i);
         assertEquals(i, ThemeSwatches.indexAt(x, y, count));
         assertEquals(i, ThemeSwatches.indexAt(x + ThemeSwatches.CELL - 1, y + ThemeSwatches.CELL - 1, count));
      }

      assertEquals(-1, ThemeSwatches.indexAt(ThemeSwatches.PAD - 1, ThemeSwatches.PAD, count), "left padding");
      assertEquals(-1, ThemeSwatches.indexAt(ThemeSwatches.PAD, ThemeSwatches.PAD - 1, count), "top padding");
      assertEquals(-1, ThemeSwatches.indexAt(ThemeSwatches.width() - 1, ThemeSwatches.PAD, count), "right padding");
      assertEquals(-1, ThemeSwatches.indexAt(ThemeSwatches.cellX(9), ThemeSwatches.cellY(9), count), "an empty cell of the last row");
   }

   @Test
   void fewShortModesAreSegmentedAndTheRestDropDown() {
      // two options of 20 px each beside a 60 px name in a 200 px row
      assertTrue(ModeControl.fitsSegmented(2, 40, 60, 200));
      assertFalse(ModeControl.fitsSegmented(2, 40, 60, 120), "too narrow: the name would be squeezed");
      assertFalse(ModeControl.fitsSegmented(ModeControl.MAX_SEGMENTS + 1, 50, 20, 400), "too many options");
      assertFalse(ModeControl.fitsSegmented(1, 10, 10, 400), "a single option is not a choice");
      assertTrue(ModeControl.fitsSegmented(ModeControl.MAX_SEGMENTS, 80, 40, 400));
   }

   @Test
   void dropdownListGrowsWithItsOptions() {
      assertTrue(ModeControl.listHeight(3) < ModeControl.listHeight(4));
      assertEquals(ModeControl.listHeight(5) - ModeControl.listHeight(4), ModeControl.listHeight(2) - ModeControl.listHeight(1));
   }
}
