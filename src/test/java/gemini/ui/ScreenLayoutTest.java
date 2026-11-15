package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenLayoutTest {
   /** GUI sizes from the smallest Minecraft allows up to a large window at GUI scale 2. */
   private static final int[][] SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}};
   private static final int[] FULL = {46, 60, 96, 82, 78, 28};
   private static final int[] SHORT = {24, 38, 22, 40, 24, 28};

   @Test
   void titleButtonsStayBetweenHeaderAndFooter() {
      for (int[] size : SIZES) {
         TitleLayout layout = TitleLayout.compute(size[0], size[1]);
         String at = size[0] + "x" + size[1];
         assertTrue(layout.buttonHeight() >= 16, "buttons stay readable at " + at);
         for (int i = 0; i < TitleLayout.BUTTONS; i++) {
            assertTrue(layout.buttonX(i) >= 4, "left edge at " + at);
            assertTrue(layout.buttonX(i) + layout.buttonWidth() <= size[0] - 4, "right edge at " + at);
            assertTrue(layout.buttonY(i) >= layout.headerY() + layout.logoSize() + 4, "below the header at " + at);
            assertTrue(layout.buttonY(i) + layout.buttonHeight() <= layout.footerY() - 4, "above the footer at " + at);
         }

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
               boolean apartX = layout.buttonX(i) + layout.buttonWidth() <= layout.buttonX(j) || layout.buttonX(j) + layout.buttonWidth() <= layout.buttonX(i);
               boolean apartY = layout.buttonY(i) + layout.buttonHeight() <= layout.buttonY(j) || layout.buttonY(j) + layout.buttonHeight() <= layout.buttonY(i);
               assertTrue(apartX || apartY, "buttons " + i + " and " + j + " at " + size[0] + "x" + size[1]);
            }
         }
      }
   }

   @Test
   void titleUsesOneColumnOnlyWhenItFits() {
      assertEquals(2, TitleLayout.compute(320, 240).columns());
      assertEquals(2, TitleLayout.compute(480, 270).columns());
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
   void dockSitsBesideTheVanillaColumnWhenThereIsRoom() {
      DockLayout layout = DockLayout.compute(960, 540, FULL, SHORT);
      assertTrue(layout.side());
      assertFalse(layout.shortLabels());
      int vanillaLeft = 960 / 2 - DockLayout.VANILLA_HALF_WIDTH;
      assertTrue(layout.panelX() + layout.panelWidth() <= vanillaLeft, "clear of the vanilla buttons");
      assertInsidePanelWithoutOverlap(layout, 960, 540);
   }

   @Test
   void dockMovesAboveTheTitleOnSmallScreens() {
      for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}}) {
         DockLayout layout = DockLayout.compute(size[0], size[1], FULL, SHORT);
         assertFalse(layout.side(), size[0] + " wide has no room beside the vanilla column");
         assertTrue(layout.panelY() + layout.panelHeight() <= DockLayout.VANILLA_TITLE_Y, "above the Game Menu title");
         assertInsidePanelWithoutOverlap(layout, size[0], size[1]);
      }

      assertTrue(DockLayout.compute(320, 240, FULL, SHORT).shortLabels(), "full labels do not fit in 320");
      int[] narrow = {30, 30, 30, 30, 30, 28};
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
