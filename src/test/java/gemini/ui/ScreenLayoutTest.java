package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenLayoutTest {
   /**
    * GUI sizes from the smallest Minecraft allows up to a large window: 1280x720 and 1920x1080 at GUI scales 1-4
    * (1280x720 at scale 4 is 320x180), plus 854x480 and the 320x240 minimum.
    */
   private static final int[][] SIZES = {
      {320, 180}, {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}
   };
   /** Pause group labels: Module menu, Edit HUD, Guide, Panic, "4 key conflicts". */
   private static final int[] FULL = {66, 48, 30, 28, 90};
   private static final int[] SHORT = {42, 18, 30, 28, 42};

   @Test
   void titleButtonsStayBetweenHeaderAndFooter() {
      for (int[] size : SIZES) {
         TitleLayout layout = TitleLayout.compute(size[0], size[1]);
         String at = size[0] + "x" + size[1];
         assertTrue(layout.buttonHeight() >= 16, "buttons stay readable at " + at);
         for (int i = 0; i < TitleLayout.BUTTONS; i++) {
            assertTrue(layout.buttonX(i) >= 4, "left edge at " + at);
            assertTrue(layout.buttonX(i) + layout.buttonW(i) <= size[0] - 4, "right edge at " + at);
            assertTrue(layout.buttonY(i) >= layout.headerY() + layout.logoSize() + 4, "below the header at " + at);
            assertTrue(layout.buttonY(i) + layout.buttonHeight() <= layout.linksY() - 4, "above the links at " + at);
         }

         assertTrue(layout.linksY() + TitleLayout.LINK_HEIGHT <= layout.footerY() - 4, "links above the footer at " + at);

         assertTrue(layout.footerY() + TitleLayout.FOOTER_HEIGHT <= layout.statusY(), "footer above the status bar at " + at);
         assertEquals(size[1], layout.statusY() + TitleLayout.STATUS_HEIGHT, "status bar on the bottom edge at " + at);
      }
   }

   @Test
   void titleButtonsNeverOverlap() {
      for (int[] size : SIZES) {
         TitleLayout layout = TitleLayout.compute(size[0], size[1]);
         for (int i = 0; i < TitleLayout.BUTTONS; i++) {
            for (int j = i + 1; j < TitleLayout.BUTTONS; j++) {
               boolean apartX = layout.buttonX(i) + layout.buttonW(i) <= layout.buttonX(j) || layout.buttonX(j) + layout.buttonW(j) <= layout.buttonX(i);
               boolean apartY = layout.buttonY(i) + layout.buttonHeight() <= layout.buttonY(j) || layout.buttonY(j) + layout.buttonHeight() <= layout.buttonY(i);
               assertTrue(apartX || apartY, "buttons " + i + " and " + j + " at " + size[0] + "x" + size[1]);
            }
         }
      }
   }

   @Test
   void titleUsesOneColumnOnlyWhenItFits() {
      assertEquals(2, TitleLayout.compute(320, 180).columns());
      assertEquals(1, TitleLayout.compute(427, 240).columns());
      assertEquals(1, TitleLayout.compute(960, 540).columns());
      assertEquals(200, TitleLayout.compute(960, 540).buttonWidth());
   }

   @Test
   void titleLayoutSurvivesTinyWindows() {
      TitleLayout layout = TitleLayout.compute(160, 120);
      assertTrue(layout.buttonHeight() >= 10);
      assertTrue(layout.buttonWidth() >= 40);
   }

   @Test
   void titleSplitsIntoTwoColumnsOfThree() {
      TitleLayout layout = TitleLayout.compute(320, 180);
      assertEquals(2, layout.columns());
      assertEquals(3, layout.rows());
      assertEquals(layout.buttonX(0), layout.buttonX(2), "first column holds buttons 0-2");
      assertTrue(layout.buttonX(3) > layout.buttonX(2), "button 3 starts the second column");
   }

   @Test
   void optionsAndQuitShareTheLastRowInOneColumn() {
      TitleLayout layout = TitleLayout.compute(960, 540);
      assertEquals(layout.buttonY(4), layout.buttonY(5));
      assertEquals(layout.buttonX(0), layout.buttonX(4));
      assertEquals(layout.buttonX(0) + layout.buttonWidth(), layout.buttonX(5) + layout.buttonW(5), "the pair spans the column");
      assertTrue(layout.buttonY(4) > layout.buttonY(3));
   }

   @Test
   void linksAreCentredInOneRow() {
      TitleLayout layout = TitleLayout.compute(854, 480);
      int[] widths = {70, 34, 44};
      int[] x = layout.linkX(widths);
      int left = x[0];
      int right = x[2] + widths[2];
      assertTrue(Math.abs((854 - right) - left) <= 1, "centred");
      assertEquals(x[0] + widths[0] + TitleLayout.LINK_SPACING, x[1]);
   }

   @Test
   void dockTopRowFitsEveryScreenWidth() {
      for (int width = 320; width <= 2000; width += 37) {
         DockLayout layout = DockLayout.compute(width, 240, FULL, SHORT);
         int last = layout.x().length - 1;
         assertTrue(layout.panelX() >= 0, "panel starts on screen at " + width);
         assertTrue(layout.x()[last] + layout.w()[last] <= width, "last button on screen at " + width);
      }
   }

   @Test
   void dockSitsBesideTheVanillaColumnWhenThereIsRoom() {
      DockLayout layout = DockLayout.compute(960, 540, FULL, SHORT);
      assertTrue(layout.side());
      assertFalse(layout.shortLabels());
      int vanillaLeft = 960 / 2 - DockLayout.VANILLA_HALF_WIDTH;
      assertTrue(layout.panelX() + layout.panelWidth() <= vanillaLeft, "clear of the vanilla buttons");
      assertInsidePanelWithoutOverlap(layout, 960, 540);
   }

   @Test
   void dockSitsInTheBottomLeftCorner() {
      DockLayout layout = DockLayout.compute(960, 540, FULL, SHORT);
      assertEquals(540 - 6, layout.panelY() + layout.panelHeight());
      assertEquals(6, layout.panelX());
   }

   @Test
   void dockMovesAboveTheTitleOnSmallScreens() {
      for (int[] size : new int[][]{{320, 180}, {320, 240}, {427, 240}}) {
         DockLayout layout = DockLayout.compute(size[0], size[1], FULL, SHORT);
         assertFalse(layout.side(), size[0] + " wide has no room beside the vanilla column");
         assertTrue(layout.panelY() + layout.panelHeight() <= DockLayout.VANILLA_TITLE_Y, "above the Game Menu title");
         assertInsidePanelWithoutOverlap(layout, size[0], size[1]);
      }

      assertTrue(DockLayout.compute(320, 240, FULL, SHORT).shortLabels(), "full labels do not fit in 320");
      int[] narrow = {24, 24, 24, 24, 24};
      assertFalse(DockLayout.compute(480, 270, narrow, narrow).shortLabels(), "full labels are kept when they fit");
   }

   @Test
   void dockShortLabelsFitTheirButtons() {
      DockLayout layout = DockLayout.compute(320, 240, FULL, SHORT);
      for (int i = 0; i < SHORT.length; i++) {
         assertTrue(layout.w()[i] >= SHORT[i] + 4, "label " + i + " fits");
      }
   }

   private static void assertInsidePanelWithoutOverlap(DockLayout layout, int screenWidth, int screenHeight) {
      assertTrue(layout.panelX() >= 0 && layout.panelX() + layout.panelWidth() <= screenWidth, "panel inside the screen horizontally");
      assertTrue(layout.panelY() >= 0 && layout.panelY() + layout.panelHeight() <= screenHeight, "panel inside the screen vertically");
      int count = layout.x().length;
      for (int i = 0; i < count; i++) {
         assertTrue(layout.x()[i] >= layout.panelX() && layout.x()[i] + layout.w()[i] <= layout.panelX() + layout.panelWidth(), "button " + i + " inside the panel");
         assertTrue(
            layout.y()[i] >= layout.panelY() && layout.y()[i] + layout.buttonHeight() <= layout.panelY() + layout.panelHeight(), "button " + i + " inside the panel"
         );
         for (int j = i + 1; j < count; j++) {
            boolean apartX = layout.x()[i] + layout.w()[i] <= layout.x()[j] || layout.x()[j] + layout.w()[j] <= layout.x()[i];
            boolean apartY = layout.y()[i] + layout.buttonHeight() <= layout.y()[j] || layout.y()[j] + layout.buttonHeight() <= layout.y()[i];
            assertTrue(apartX || apartY, "buttons " + i + " and " + j + " overlap");
         }
      }
   }
}
