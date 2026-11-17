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

   /** GUI scale for each entry of {@link #SIZES} when the window is 1280x720 or 1920x1080 (the others use 2). */
   private static int scaleFor(int[] size) {
      if (size[0] * 4 == 1280 || size[0] * 4 == 1920) {
         return 4;
      }

      return size[0] * 3 == 1280 || size[0] * 3 == 1920 ? 3 : 2;
   }

   @Test
   void titleMenuStaysOnScreenAndAboveTheFooter() {
      for (int[] size : SIZES) {
         for (boolean resume : new boolean[]{false, true}) {
            TitleLayout layout = TitleLayout.compute(size[0], size[1], scaleFor(size), resume);
            String at = size[0] + "x" + size[1] + (resume ? " with Continue" : "");
            assertTrue(layout.buttonHeight() >= 16, "buttons stay readable at " + at);
            for (int i = 0; i < TitleLayout.SLOTS; i++) {
               if (!layout.has(i)) {
                  continue;
               }

               assertTrue(layout.x(i) >= 4, "left edge at " + at);
               assertTrue(layout.x(i) + layout.w(i) <= size[0] - 4, "right edge at " + at);
               assertTrue(layout.y(i) >= layout.logoY() + layout.logoSize(), "below the logo at " + at);
            }

            assertTrue(layout.linksY() >= layout.menuBottom(), "links under the menu at " + at);
            assertTrue(layout.linksY() + TitleLayout.LINK_HEIGHT <= layout.footerY(), "links above the footer at " + at);
            assertEquals(size[1], layout.footerY() + TitleLayout.FOOTER_HEIGHT, "footer on the bottom edge at " + at);
            assertTrue(layout.logoY() >= TitleLayout.EDGE, "logo below the top edge at " + at);
         }
      }
   }

   @Test
   void titleSlotsNeverOverlap() {
      for (int[] size : SIZES) {
         TitleLayout layout = TitleLayout.compute(size[0], size[1], scaleFor(size), true);
         for (int i = 0; i < TitleLayout.SLOTS; i++) {
            for (int j = i + 1; j < TitleLayout.SLOTS; j++) {
               boolean apartX = layout.x(i) + layout.w(i) <= layout.x(j) || layout.x(j) + layout.w(j) <= layout.x(i);
               boolean apartY = layout.y(i) + layout.buttonHeight() <= layout.y(j) || layout.y(j) + layout.buttonHeight() <= layout.y(i);
               assertTrue(apartX || apartY, "slots " + i + " and " + j + " at " + size[0] + "x" + size[1]);
            }
         }
      }
   }

   @Test
   void continueOnlyWhenThereIsARecentWorld() {
      TitleLayout without = TitleLayout.compute(640, 360, 2, false);
      TitleLayout with = TitleLayout.compute(640, 360, 2, true);
      assertFalse(without.has(TitleLayout.CONTINUE));
      assertTrue(with.has(TitleLayout.CONTINUE));
      assertEquals(with.y(TitleLayout.SINGLEPLAYER) + with.buttonHeight() + 4, with.y(TitleLayout.CONTINUE), "Continue sits right under Singleplayer");
   }

   @Test
   void titleGroupsAreSetApartByExtraSpace() {
      TitleLayout l = TitleLayout.compute(640, 360, 2, false);
      int inGroup = l.y(TitleLayout.REALMS) - (l.y(TitleLayout.MULTIPLAYER) + l.buttonHeight());
      int betweenGroups = l.y(TitleLayout.MULTIPLAYER) - (l.y(TitleLayout.SINGLEPLAYER) + l.buttonHeight());
      assertTrue(betweenGroups > inGroup, "groups read as groups");
   }

   @Test
   void titleColumnSitsLeftOnWideScreensAndCentredOnNarrowOnes() {
      TitleLayout wide = TitleLayout.compute(640, 360, 2, true);
      assertFalse(wide.centred());
      assertTrue(wide.columnX() + wide.columnWidth() < 640 / 2 + 40, "the panorama stays visible on the right");
      TitleLayout narrow = TitleLayout.compute(320, 240, 4, true);
      assertTrue(narrow.centred());
      assertEquals(320 - narrow.columnX() - narrow.columnWidth(), narrow.columnX(), "centred");
   }

   @Test
   void logoIsDrawnAtAWholeRatioOfItsTexture() {
      assertEquals(80, TitleLayout.compute(640, 360, 2, true).logoSize(), "native size at GUI scale 2");
      for (int[] size : SIZES) {
         int scale = scaleFor(size);
         int logo = TitleLayout.compute(size[0], size[1], scale, true).logoSize();
         if (logo > 0) {
            int pixels = logo * scale;
            assertTrue(Math.abs(pixels - 160) <= 3 || Math.abs(pixels - 80) <= 3 || Math.abs(pixels - 40) <= 3, logo + " at scale " + scale);
         }
      }
   }

   @Test
   void logoIsDrawnInWholeTextureRatiosAndFitsItsRoom() {
      for (int[] size : SIZES) {
         for (int scale = 1; scale <= 4; scale++) {
            TitleLayout l = TitleLayout.compute(size[0], size[1], scale, true);
            int pixels = l.logoPixels();
            assertTrue(pixels == 0 || pixels == 160 || pixels == 80 || pixels == 40, pixels + " window pixels at scale " + scale);
            assertEquals(pixels == 0, l.logoSize() == 0, "room only for a drawn logo");
            assertTrue(l.logoSize() * scale >= pixels && l.logoSize() * scale < pixels + scale, "room is the logo rounded up at scale " + scale);
         }
      }

      assertEquals(160, TitleLayout.compute(1280 / 3, 720 / 3, 3, true).logoPixels(), "full size at 1280x720, GUI scale 3");
   }

   @Test
   void optionsAndQuitShareTheLastRow() {
      TitleLayout l = TitleLayout.compute(960, 540, 2, true);
      assertEquals(l.y(TitleLayout.OPTIONS), l.y(TitleLayout.QUIT));
      assertEquals(l.x(TitleLayout.SINGLEPLAYER), l.x(TitleLayout.OPTIONS));
      assertEquals(l.x(TitleLayout.SINGLEPLAYER) + l.columnWidth(), l.x(TitleLayout.QUIT) + l.w(TitleLayout.QUIT), "the pair spans the column");
   }

   @Test
   void titleLayoutSurvivesTinyWindows() {
      TitleLayout layout = TitleLayout.compute(160, 120, 1, true);
      assertTrue(layout.buttonHeight() >= 16);
      assertTrue(layout.columnWidth() >= 100);
   }

   @Test
   void linksStartUnderTheColumnAndFitInIt() {
      TitleLayout layout = TitleLayout.compute(854, 480, 2, false);
      int[] widths = {40, 34, 40, 30};
      int[] x = layout.linkX(widths);
      assertEquals(layout.columnX(), x[0], "left-aligned with the column");
      assertEquals(x[0] + widths[0] + TitleLayout.LINK_SPACING, x[1]);
      TitleLayout narrow = TitleLayout.compute(320, 180, 4, false);
      int[] wide = {56, 56, 56};
      int[] nx = narrow.linkX(wide);
      assertTrue(nx[2] + wide[2] <= narrow.columnX() + narrow.columnWidth() + 1, "links squeeze into a narrow column");
   }

   @Test
   void lastPlayedReadsNaturally() {
      assertEquals("just now", TitleLayout.lastPlayed(20_000L));
      assertEquals("1 minute ago", TitleLayout.lastPlayed(61_000L));
      assertEquals("5 minutes ago", TitleLayout.lastPlayed(5 * 60_000L));
      assertEquals("2 hours ago", TitleLayout.lastPlayed(2 * 3_600_000L + 5_000L));
      assertEquals("yesterday", TitleLayout.lastPlayed(30 * 3_600_000L));
      assertEquals("3 days ago", TitleLayout.lastPlayed(3 * 86_400_000L));
      assertEquals("just now", TitleLayout.lastPlayed(-5_000L), "clock skew");
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
