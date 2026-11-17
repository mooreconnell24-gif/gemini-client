package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenLayoutTest {
   /**
    * GUI sizes from the smallest Minecraft allows up to a large window: 1280x720 and 1920x1080 at GUI scales 1-4
    * (1280x720 at scale 4 is 320x180), plus 854x480 at scales 2 and 3 (427x240, 285x160) and the 320x240 minimum.
    */
   private static final int[][] SIZES = {
      {285, 160}, {320, 180}, {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1280, 720}, {1920, 1080}
   };
   /** Pause bar labels: Modules, Edit HUD, Mods, Guide, Panic, "4 key conflicts". */
   private static final int[] FULL = {42, 48, 26, 30, 28, 90};
   private static final int[] SHORT = {42, 22, 26, 30, 28, 42};

   /** GUI scale for each entry of {@link #SIZES} when the window is 1280x720 or 1920x1080 (the others use 2). */
   private static int scaleFor(int[] size) {
      if (size[0] * 4 == 1280 || size[0] * 4 == 1920) {
         return 4;
      }

      return size[0] * 3 == 1280 || size[0] * 3 == 1920 || size[0] == 285 ? 3 : 2;
   }

   // ---- home screen ----------------------------------------------------------------------------------------

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
               assertTrue(layout.y(i) >= layout.headerY() + layout.headerHeight(), "below the header at " + at);
            }

            assertTrue(layout.linksY() >= layout.menuBottom(), "links under the menu at " + at);
            assertTrue(layout.columnBottom() <= layout.footerY(), "links above the footer at " + at);
            assertEquals(size[1], layout.footerY() + TitleLayout.FOOTER_HEIGHT, "footer on the bottom edge at " + at);
            assertTrue(layout.headerY() >= TitleLayout.EDGE, "header below the top edge at " + at);
            if (layout.centred()) {
               assertTrue(layout.headerY() >= TitleLayout.EDGE + TitleLayout.CORNER_HEIGHT, "a centred column starts under the corner links at " + at);
            }
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
      int gap = with.y(TitleLayout.CONTINUE) - (with.y(TitleLayout.SINGLEPLAYER) + with.buttonHeight());
      assertTrue(gap > 0 && gap <= 6, "Continue sits right under Singleplayer");
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
      assertTrue(wide.columnX() + wide.columnWidth() < 640 / 2 + 40, "the backdrop stays visible on the right");
      TitleLayout narrow = TitleLayout.compute(320, 240, 4, true);
      assertTrue(narrow.centred());
      assertEquals(320 - narrow.columnX() - narrow.columnWidth(), narrow.columnX(), "centred");
      assertEquals((320 - 90) / 2, narrow.headerX(90), "the header is centred with the column");
      assertEquals(wide.columnX(), wide.headerX(90), "and starts at the column's left edge otherwise");
   }

   @Test
   void theTargetWindowsGetTheFullHeader() {
      // 1280x720 at GUI scale 2 and 3: the emblem, the large name and the line under it, and tiles with icons.
      for (int[] size : new int[][]{{640, 360, 2}, {427, 240, 3}}) {
         TitleLayout l = TitleLayout.compute(size[0], size[1], size[2], true);
         String at = size[0] + "x" + size[1];
         assertEquals(TitleLayout.HEADER_FULL, l.header(), "full header at " + at);
         assertTrue(l.logoSize() > 0, "emblem at " + at);
         assertTrue(l.linkTiles(), "icon tiles at " + at);
         assertTrue(l.buttonHeight() >= 20, "roomy buttons at " + at);
         assertTrue(l.taglineY() + TitleLayout.TAGLINE_CAP <= l.headerY() + l.headerHeight(), "the line under the name stays in the header at " + at);
         assertTrue(l.nameY() >= l.headerY(), "the name starts inside the header at " + at);
      }

      // 854x480 at GUI scale 3 is 285x160: everything still fits, with the header dropped and plain links.
      TitleLayout small = TitleLayout.compute(285, 160, 3, true);
      assertTrue(small.centred());
      assertFalse(small.linkTiles());
      assertTrue(small.columnBottom() <= small.footerY());
   }

   @Test
   void emblemIsDrawnInWholeTextureRatiosAndFitsItsRoom() {
      for (int[] size : SIZES) {
         for (int scale = 1; scale <= 4; scale++) {
            TitleLayout l = TitleLayout.compute(size[0], size[1], scale, true);
            int pixels = l.logoPixels();
            assertTrue(pixels == 0 || pixels == 80 || pixels == 40, pixels + " window pixels at scale " + scale);
            assertEquals(pixels == 0, l.logoSize() == 0, "room only for a drawn emblem");
            assertTrue(l.logoSize() * scale >= pixels && l.logoSize() * scale < pixels + scale, "room is the emblem rounded up at scale " + scale);
            assertTrue(l.logoSize() <= l.headerHeight(), "the emblem fits the header");
            if (l.header() != TitleLayout.HEADER_FULL) {
               assertEquals(0, pixels, "only the full header has the emblem");
            }
         }
      }

      assertEquals(80, TitleLayout.compute(640, 360, 2, true).logoPixels(), "half the texture at 1280x720, GUI scale 2");
      assertEquals(40, TitleLayout.compute(640, 360, 2, true).logoSize());
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
      assertFalse(layout.hasCard());
   }

   @Test
   void quickLinksShareTheColumnExactly() {
      for (int[] size : SIZES) {
         TitleLayout l = TitleLayout.compute(size[0], size[1], scaleFor(size), false);
         assertEquals(l.columnX(), l.linkX(0), "the first tile starts at the column's left edge");
         int last = TitleLayout.LINKS - 1;
         assertEquals(l.columnX() + l.columnWidth(), l.linkX(last) + l.linkW(last), "the last tile ends at its right edge");
         for (int i = 0; i < last; i++) {
            assertEquals(TitleLayout.LINK_GAP, l.linkX(i + 1) - (l.linkX(i) + l.linkW(i)), "even gaps");
            assertTrue(Math.abs(l.linkW(i) - l.linkW(i + 1)) <= 1, "tiles are the same width, give or take a pixel");
         }
      }
   }

   @Test
   void newsCardStaysClearOfTheColumnTheFooterAndTheEdges() {
      for (int[] size : SIZES) {
         TitleLayout l = TitleLayout.compute(size[0], size[1], scaleFor(size), true);
         String at = size[0] + "x" + size[1];
         if (!l.hasCard()) {
            assertTrue(l.centred() || size[0] - (l.columnX() + l.columnWidth()) < TitleLayout.CARD_MIN + 40, "no card only where there is no room at " + at);
            continue;
         }

         assertTrue(l.cardWidth() >= TitleLayout.CARD_MIN && l.cardWidth() <= TitleLayout.CARD_MAX, "card width at " + at);
         assertTrue(l.cardX() >= l.columnX() + l.columnWidth() + 16, "clear of the menu column at " + at);
         assertTrue(l.cardX() + l.cardWidth() <= size[0] - TitleLayout.EDGE, "inside the right edge at " + at);
         assertTrue(l.cardBottom() < l.footerY(), "above the footer at " + at);
      }

      assertTrue(TitleLayout.compute(640, 360, 2, true).hasCard(), "1280x720 at GUI scale 2 has the card");
      assertTrue(TitleLayout.compute(427, 240, 3, true).hasCard(), "and at GUI scale 3");
      assertFalse(TitleLayout.compute(285, 160, 3, true).hasCard(), "854x480 at GUI scale 3 has no room for it");
   }

   @Test
   void entranceIsStaggeredAndEnds() {
      assertEquals(0.0F, TitleLayout.entrance(0L, 0, 45, 400), 1.0E-6F);
      assertEquals(0.5F, TitleLayout.entrance(200L, 0, 45, 400), 1.0E-6F);
      assertEquals(1.0F, TitleLayout.entrance(400L, 0, 45, 400), 1.0E-6F);
      assertEquals(0.0F, TitleLayout.entrance(90L, 2, 45, 400), 1.0E-6F, "a later element has not started yet");
      assertTrue(TitleLayout.entrance(300L, 1, 45, 400) < TitleLayout.entrance(300L, 0, 45, 400), "elements arrive in order");
      assertEquals(1.0F, TitleLayout.entrance(5_000L, 12, 45, 400), 1.0E-6F, "everything has arrived in the end");
      assertEquals(1.0F, TitleLayout.entrance(0L, 3, 45, 0), 1.0E-6F, "no duration means no animation");
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

   // ---- pause bar ------------------------------------------------------------------------------------------

   @Test
   void pauseBarFitsEveryScreenWidthAboveTheVanillaTitle() {
      for (int width = 200; width <= 2000; width += 37) {
         for (int count = 5; count <= 6; count++) {
            DockLayout layout = DockLayout.compute(width, 240, java.util.Arrays.copyOf(FULL, count), java.util.Arrays.copyOf(SHORT, count));
            assertInsidePanelWithoutOverlap(layout, width, 240);
            assertTrue(layout.panelY() + layout.panelHeight() <= DockLayout.VANILLA_TITLE_Y, "above the Game Menu title at " + width);
            assertEquals(width - layout.panelX() - layout.panelWidth(), layout.panelX(), 1, "centred at " + width);
         }
      }
   }

   @Test
   void pauseBarUsesTheLongestLabelsThatFit() {
      assertEquals(DockLayout.FULL, DockLayout.compute(640, 360, FULL, SHORT).mode(), "full labels at 1280x720, GUI scale 2");
      assertEquals(DockLayout.FULL, DockLayout.compute(427, 240, java.util.Arrays.copyOf(FULL, 5), java.util.Arrays.copyOf(SHORT, 5)).mode(), "and at GUI scale 3");
      DockLayout tight = DockLayout.compute(420, 240, FULL, SHORT);
      assertTrue(tight.shortLabels(), "short labels when the full ones do not fit");
      DockLayout tiny = DockLayout.compute(220, 160, FULL, SHORT);
      assertTrue(tiny.iconsOnly(), "icons alone when even the short labels do not fit");
      for (int w : tiny.w()) {
         assertTrue(w >= DockLayout.ICON, "an icon fits its entry");
      }
   }

   @Test
   void pauseBarEntriesHaveRoomForIconAndLabel() {
      DockLayout full = DockLayout.compute(960, 540, FULL, SHORT);
      for (int i = 0; i < FULL.length; i++) {
         assertTrue(full.w()[i] >= DockLayout.ICON + DockLayout.ICON_GAP + FULL[i] + 8, "entry " + i + " fits its icon and label");
      }

      DockLayout brief = DockLayout.compute(420, 240, FULL, SHORT);
      for (int i = 0; i < SHORT.length; i++) {
         assertTrue(brief.w()[i] >= DockLayout.ICON + DockLayout.ICON_GAP + SHORT[i] + 8, "entry " + i + " fits its icon and short label");
      }

      assertEquals(DockLayout.BAR_HEIGHT, full.panelHeight());
      assertEquals(full.panelY() + DockLayout.INSET, full.y()[0], "entries are inset evenly in the pill");
      assertEquals(full.panelX() + DockLayout.INSET, full.x()[0]);
   }

   private static void assertInsidePanelWithoutOverlap(DockLayout layout, int screenWidth, int screenHeight) {
      assertTrue(layout.panelX() >= 0 && layout.panelX() + layout.panelWidth() <= screenWidth, "panel inside the screen horizontally at " + screenWidth);
      assertTrue(layout.panelY() >= 0 && layout.panelY() + layout.panelHeight() <= screenHeight, "panel inside the screen vertically");
      int count = layout.x().length;
      for (int i = 0; i < count; i++) {
         assertTrue(layout.x()[i] >= layout.panelX() && layout.x()[i] + layout.w()[i] <= layout.panelX() + layout.panelWidth(), "button " + i + " inside the panel");
         assertTrue(
            layout.y()[i] >= layout.panelY() && layout.y()[i] + layout.buttonHeight() <= layout.panelY() + layout.panelHeight(), "button " + i + " inside the panel"
         );
         for (int j = i + 1; j < count; j++) {
            boolean apartX = layout.x()[i] + layout.w()[i] <= layout.x()[j] || layout.x()[j] + layout.w()[j] <= layout.x()[i];
            assertTrue(apartX, "buttons " + i + " and " + j + " overlap");
         }
      }
   }

   // ---- windowed screens -----------------------------------------------------------------------------------

   @Test
   void windowsAreCentredAndStayOnScreen() {
      for (int[] size : SIZES) {
         int[] w = ScreenBounds.window(size[0], size[1], 440, 300);
         assertTrue(w[0] >= ScreenBounds.MARGIN && w[1] >= ScreenBounds.MARGIN, "margins at " + size[0] + "x" + size[1]);
         assertTrue(w[0] + w[2] <= size[0] - ScreenBounds.MARGIN && w[1] + w[3] <= size[1] - ScreenBounds.MARGIN);
         assertTrue(w[2] <= 440 && w[3] <= 300);
         assertEquals(size[0] - w[0] - w[2], w[0], 1, "centred horizontally");
         assertEquals(size[1] - w[1] - w[3], w[1], 1, "centred vertically");
         // header + footer leave room for content even in the smallest window
         assertTrue(w[3] - GlassWindow.HEADER - GlassWindow.FOOTER >= 24, "content room at " + size[0] + "x" + size[1]);
      }
   }

   @Test
   void buttonRowsFillTheirSpan() {
      for (int span : new int[]{180, 245, 416}) {
         int[] x = ScreenBounds.row(10, 10 + span, 4, 4);
         int w = ScreenBounds.rowWidth(10, 10 + span, 4, 4);
         assertEquals(10, x[0]);
         for (int i = 1; i < 4; i++) {
            assertEquals(x[i - 1] + w + 4, x[i], "even gaps");
         }

         assertTrue(x[3] + w <= 10 + span && x[3] + w > 10 + span - 4, "the row ends at the span's right edge, give or take the rounding");
      }

      assertEquals(GlassWindow.PAD, ScreenBounds.pad(440));
      assertTrue(ScreenBounds.pad(200) < GlassWindow.PAD, "a narrow window has tighter padding");
   }

   @Test
   void scrollbarThumbIsProportionalAndBounded() {
      assertEquals(100, GlassWindow.thumbSize(100, 0.0F), "content that fits fills the track");
      assertEquals(50, GlassWindow.thumbSize(100, 100.0F), "twice the content, half the track");
      assertEquals(14, GlassWindow.thumbSize(100, 100_000.0F), "never smaller than a grabbable minimum");
      assertEquals(0, GlassWindow.thumbSize(0, 10.0F));
      assertEquals("abc…", GlassWindow.singleEllipsis("abc……", "…"));
      assertEquals("abc…", GlassWindow.singleEllipsis("abc…", "…"));
      assertEquals("abc..", GlassWindow.singleEllipsis("abc....", ".."));
   }

   // ---- small pure helpers ---------------------------------------------------------------------------------

   @Test
   void translucentStrokesAddUpToTheirOpacity() {
      assertEquals(255, GlassIcon.dabAlpha(255, 4), "an opaque stroke stays opaque");
      assertEquals(128, GlassIcon.dabAlpha(128, 1));
      for (int alpha : new int[]{40, 128, 200}) {
         for (int layers = 2; layers <= 6; layers++) {
            int each = GlassIcon.dabAlpha(alpha, layers);
            double total = 255.0 * (1.0 - Math.pow(1.0 - each / 255.0, layers));
            assertTrue(Math.abs(total - alpha) <= 8.0, layers + " layers of " + each + " give " + total + ", wanted " + alpha);
            assertTrue(each < alpha, "each layer is fainter than the whole");
         }
      }
   }

   @Test
   void toastTimerRunsDownAndStops() {
      assertEquals(1.0F, GlassToast.remaining(0L, 4000L), 1.0E-6F);
      assertEquals(0.5F, GlassToast.remaining(2000L, 4000L), 1.0E-6F);
      assertEquals(0.0F, GlassToast.remaining(9000L, 4000L), 1.0E-6F);
      assertEquals(0.0F, GlassToast.remaining(10L, 0L), 1.0E-6F);
   }
}
