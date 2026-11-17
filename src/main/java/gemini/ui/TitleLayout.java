package gemini.ui;

/**
 * Where everything on {@link GeminiTitleScreen} goes for a given GUI size. Pure arithmetic, so it can be tested
 * without a running game.
 *
 * <p>The screen is one column: the logo, then the menu in three groups separated by a little extra space
 * (Singleplayer and, when there is a recent world, Continue; Multiplayer and Realms; Options and Quit sharing a
 * row as on the vanilla title screen), then one row of small text links. The column sits in the left part of a
 * wide screen so the panorama stays visible beside it, and in the middle of a narrow one. The footer row along the
 * bottom edge holds the version, the vanilla-menu link and the Mojang notice; the sandbox switch is in the top-right
 * corner.
 *
 * <p>The logo is drawn at its texture's native pixel size (160 screen pixels, i.e. {@code 160 / guiScale}) when
 * there is room, otherwise at half or a quarter of it, so it is never resampled at an odd ratio. When even that
 * does not fit, the buttons get slimmer, and as a last resort the logo is left out.
 */
public record TitleLayout(
   int width,
   int height,
   boolean centred,
   int columnX,
   int columnWidth,
   int logoSize,
   int logoX,
   int logoY,
   int buttonHeight,
   int[] slotX,
   int[] slotY,
   int[] slotW,
   int linksY,
   int footerY
) {
   public static final int SINGLEPLAYER = 0;
   public static final int CONTINUE = 1;
   public static final int MULTIPLAYER = 2;
   public static final int REALMS = 3;
   public static final int OPTIONS = 4;
   public static final int QUIT = 5;
   public static final int SLOTS = 6;
   /** Pixel size of the logo texture. */
   public static final int LOGO_TEXTURE = 160;
   public static final int FOOTER_HEIGHT = 12;
   public static final int LINK_HEIGHT = 12;
   /** Horizontal space between two text links. */
   public static final int LINK_SPACING = 10;
   /** Height of the sandbox switch row in the top-right corner. */
   public static final int CORNER_HEIGHT = 14;
   public static final int EDGE = 6;
   private static final int MAX_COLUMN = 180;
   private static final int MIN_COLUMN = 100;
   private static final int PAIR_GAP = 4;
   /** Below this width the column is centred instead of sitting on the left. */
   private static final int CENTRE_BELOW = 400;
   /** Button height, gap between rows of a group, gap between groups, gap under the logo, gap above the links. */
   private static final int[][] DENSITIES = {{20, 4, 10, 18, 10}, {18, 3, 7, 12, 8}, {16, 2, 4, 6, 4}};

   /**
    * @param guiScale the window's GUI scale (only used to draw the logo at a whole-number ratio; 0 or less means 2)
    * @param hasContinue whether there is a recent world to continue, which adds a row under Singleplayer
    */
   public static TitleLayout compute(int width, int height, int guiScale, boolean hasContinue) {
      int scale = guiScale <= 0 ? 2 : guiScale;
      boolean centred = width < CENTRE_BELOW;
      int top = centred ? EDGE + CORNER_HEIGHT + 4 : EDGE + 6;
      int footerY = height - FOOTER_HEIGHT;
      int bottom = footerY - 4;
      int available = Math.max(0, bottom - top);
      int big = Math.min(96, Math.round((float)LOGO_TEXTURE / scale));
      int half = Math.round(LOGO_TEXTURE / 2.0F / scale);
      int quarter = Math.round(LOGO_TEXTURE / 4.0F / scale);
      // Preference order: normal buttons with the full-size logo, then slimmer buttons, then smaller logos.
      int[][] tries = {{0, big}, {1, big}, {0, half}, {1, half}, {2, half}, {2, quarter}, {2, 0}};
      int[] density = DENSITIES[2];
      int logo = 0;
      int total = 0;
      for (int[] attempt : tries) {
         int[] d = DENSITIES[attempt[0]];
         int size = attempt[1] < 20 ? 0 : attempt[1];
         int needed = stackHeight(d, size, hasContinue);
         density = d;
         logo = size;
         total = needed;
         if (needed <= available) {
            break;
         }
      }

      if (total > available && logo > 0) {
         logo = 0;
         total = stackHeight(density, 0, hasContinue);
      }

      int columnWidth = Math.max(Math.min(MIN_COLUMN, width - 2 * EDGE), Math.min(MAX_COLUMN, width - 32));
      int columnX = centred ? (width - columnWidth) / 2 : Math.max(16, Math.min(120, Math.round(width * 0.1F)));
      // A little above the middle reads as intentional; exactly centred looks like it sank.
      int y = top + Math.max(0, Math.round((available - total) * 0.42F));
      int logoY = y;
      int logoX = centred ? columnX + (columnWidth - logo) / 2 : columnX;
      if (logo > 0) {
         y += logo + density[3];
      }

      int h = density[0];
      int row = density[1];
      int group = density[2];
      int[] slotX = new int[SLOTS];
      int[] slotY = new int[SLOTS];
      int[] slotW = new int[SLOTS];
      for (int i = 0; i < SLOTS; i++) {
         slotX[i] = columnX;
         slotW[i] = columnWidth;
      }

      slotY[SINGLEPLAYER] = y;
      y += h;
      if (hasContinue) {
         y += row;
         slotY[CONTINUE] = y;
         y += h;
      } else {
         slotY[CONTINUE] = -1;
      }

      y += group;
      slotY[MULTIPLAYER] = y;
      y += h + row;
      slotY[REALMS] = y;
      y += h + group;
      slotY[OPTIONS] = y;
      slotY[QUIT] = y;
      int left = (columnWidth - PAIR_GAP) / 2;
      slotW[OPTIONS] = left;
      slotX[QUIT] = columnX + left + PAIR_GAP;
      slotW[QUIT] = columnWidth - left - PAIR_GAP;
      y += h;
      int linksY = y + density[4];
      return new TitleLayout(width, height, centred, columnX, columnWidth, logo, logoX, logoY, h, slotX, slotY, slotW, linksY, footerY);
   }

   /** Height of logo + menu + links for one density. */
   static int stackHeight(int[] density, int logo, boolean hasContinue) {
      int rows = hasContinue ? 5 : 4;
      int menu = rows * density[0] + (hasContinue ? 2 : 1) * density[1] + 2 * density[2];
      return (logo > 0 ? logo + density[3] : 0) + menu + density[4] + LINK_HEIGHT;
   }

   public boolean has(int slot) {
      return this.slotY[slot] >= 0;
   }

   public int x(int slot) {
      return this.slotX[slot];
   }

   public int y(int slot) {
      return this.slotY[slot];
   }

   public int w(int slot) {
      return this.slotW[slot];
   }

   /** Bottom edge of the menu (the Options / Quit row). */
   public int menuBottom() {
      return this.slotY[OPTIONS] + this.buttonHeight;
   }

   /**
    * Left edges of the text links, given each link's width: one row under the menu, starting at the column's left
    * edge (centred under it when the column is centred), {@link #LINK_SPACING} apart. When the row would not fit in
    * the column, the spacing shrinks.
    */
   public int[] linkX(int[] widths) {
      int total = 0;
      for (int width : widths) {
         total += width;
      }

      int gaps = Math.max(0, widths.length - 1);
      int spacing = LINK_SPACING;
      if (gaps > 0 && total + gaps * spacing > this.columnWidth) {
         spacing = Math.max(2, (this.columnWidth - total) / gaps);
      }

      total += gaps * spacing;
      int[] x = new int[widths.length];
      int cursor = this.centred ? this.columnX + (this.columnWidth - total) / 2 : this.columnX;
      for (int i = 0; i < widths.length; i++) {
         x[i] = cursor;
         cursor += widths[i] + spacing;
      }

      return x;
   }

   /** How long ago a world was last played, in the words of the Continue tooltip: "just now", "5 minutes ago", "3 days ago". */
   public static String lastPlayed(long ageMs) {
      long minutes = Math.max(0L, ageMs) / 60_000L;
      if (minutes < 1L) {
         return "just now";
      }

      if (minutes < 60L) {
         return minutes == 1L ? "1 minute ago" : minutes + " minutes ago";
      }

      long hours = minutes / 60L;
      if (hours < 24L) {
         return hours == 1L ? "1 hour ago" : hours + " hours ago";
      }

      long days = hours / 24L;
      return days == 1L ? "yesterday" : days + " days ago";
   }
}
