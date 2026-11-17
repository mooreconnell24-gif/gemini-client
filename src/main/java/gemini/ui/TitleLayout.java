package gemini.ui;

/**
 * Where everything on {@link GeminiTitleScreen} goes for a given GUI size. Pure arithmetic, so it can be tested
 * without a running game.
 *
 * <p>The screen is one column: a header (the emblem beside the name in the large UI font, with one quiet line
 * under it), then the menu in three groups separated by a little extra space (Singleplayer and, when there is a
 * recent world, Continue; Multiplayer and Realms; Options and Quit sharing a row as on the vanilla title screen),
 * then a row of four quick-link tiles. The column sits in the left part of a wide screen so the backdrop stays
 * visible beside it, and in the middle of a narrow one. On a wide screen a small "what's new" card sits in the
 * bottom-right corner. The footer row along the bottom edge holds the version, the theme swatches and the Mojang
 * notice; the vanilla-menu link and the sandbox switch are in the top corners.
 *
 * <p>The emblem is drawn in window pixels at half or a quarter of its texture's size, so it is never resampled at
 * an odd ratio, whatever the GUI scale ({@link #logoPixels}); {@link #logoSize} is the room it takes in GUI units,
 * rounded up. When the full header does not fit, the buttons get slimmer, then the header shrinks to the name
 * alone in a smaller size, and as a last resort it is left out.
 */
public record TitleLayout(
   int width,
   int height,
   boolean centred,
   int columnX,
   int columnWidth,
   int header,
   int headerY,
   int headerHeight,
   int logoSize,
   int logoPixels,
   int buttonHeight,
   int[] slotX,
   int[] slotY,
   int[] slotW,
   int linksY,
   int linksHeight,
   int footerY,
   int cardX,
   int cardWidth,
   int cardBottom
) {
   public static final int SINGLEPLAYER = 0;
   public static final int CONTINUE = 1;
   public static final int MULTIPLAYER = 2;
   public static final int REALMS = 3;
   public static final int OPTIONS = 4;
   public static final int QUIT = 5;
   public static final int SLOTS = 6;
   /** Header variants: the emblem with the large name and the line under it; the name alone, smaller; nothing. */
   public static final int HEADER_FULL = 0;
   public static final int HEADER_NAME = 1;
   public static final int HEADER_NONE = 2;
   /** Pixel size of the logo texture. */
   public static final int LOGO_TEXTURE = 160;
   public static final int FOOTER_HEIGHT = 12;
   /** Height of a quick-link tile with its icon above its caption, and of one with the caption alone. */
   public static final int LINK_TILE = 30;
   public static final int LINK_ROW = 16;
   /** Number of quick links and the gap between two of them. */
   public static final int LINKS = 4;
   public static final int LINK_GAP = 4;
   /** Height of the switch and link in the top corners. */
   public static final int CORNER_HEIGHT = 14;
   public static final int EDGE = 6;
   /** Corner radius of the menu buttons and tiles. */
   public static final int BUTTON_RADIUS = 6;
   /** Gap between the emblem and the name, and between the name and the line under it. */
   public static final int HEADER_GAP = 8;
   public static final int TAGLINE_GAP = 6;
   /** Capital height of the name in the full and the small header, and of the line under it. */
   public static final int NAME_CAP = 20;
   public static final int NAME_CAP_SMALL = 13;
   public static final int TAGLINE_CAP = 7;
   /** The "what's new" card: its width range, its distance from the screen's right edge and from the footer. */
   public static final int CARD_MAX = 220;
   public static final int CARD_MIN = 150;
   public static final int CARD_MARGIN = 16;
   private static final int MAX_COLUMN = 200;
   private static final int MIN_COLUMN = 100;
   private static final int PAIR_GAP = 4;
   private static final int MIN_LOGO = 20;
   /** Below this width the column is centred instead of sitting on the left. */
   private static final int CENTRE_BELOW = 400;
   /** Button height, gap between rows of a group, gap between groups, gap under the header, gap above the links, link height. */
   private static final int[][] DENSITIES = {
      {24, 5, 12, 18, 12, LINK_TILE},
      {20, 4, 8, 12, 8, LINK_TILE},
      {18, 3, 6, 8, 6, LINK_ROW},
      {16, 2, 4, 6, 4, LINK_ROW}
   };

   /**
    * @param guiScale the window's GUI scale (only used to draw the emblem at a whole-number ratio; 0 or less means 2)
    * @param hasContinue whether there is a recent world to continue, which adds a row under Singleplayer
    */
   public static TitleLayout compute(int width, int height, int guiScale, boolean hasContinue) {
      int scale = guiScale <= 0 ? 2 : guiScale;
      boolean centred = width < CENTRE_BELOW;
      int top = centred ? EDGE + CORNER_HEIGHT + 4 : EDGE + 6;
      int footerY = height - FOOTER_HEIGHT;
      int bottom = footerY - 4;
      int available = Math.max(0, bottom - top);
      // Preference order: roomy buttons with the full header, then slimmer buttons, then smaller headers.
      int[][] tries = {{0, HEADER_FULL}, {1, HEADER_FULL}, {1, HEADER_NAME}, {2, HEADER_NAME}, {3, HEADER_NAME}, {3, HEADER_NONE}};
      int[] density = DENSITIES[3];
      int header = HEADER_NONE;
      int headerHeight = 0;
      int total = 0;
      for (int[] attempt : tries) {
         density = DENSITIES[attempt[0]];
         header = attempt[1];
         headerHeight = headerHeight(header, scale);
         total = stackHeight(density, headerHeight, hasContinue);
         if (total <= available) {
            break;
         }
      }

      int logoPixels = header == HEADER_FULL ? logoPixels(scale) : 0;
      int logo = logoUnits(logoPixels, scale);
      // On a wide screen the column takes at most about two fifths of the width, which leaves room for the card.
      int widest = centred ? MAX_COLUMN : Math.min(MAX_COLUMN, Math.max(160, Math.round(width * 0.42F)));
      int columnWidth = Math.max(Math.min(MIN_COLUMN, width - 2 * EDGE), Math.min(widest, width - 32));
      int columnX = centred ? (width - columnWidth) / 2 : Math.max(16, Math.min(120, Math.round(width * 0.1F)));
      // A little above the middle reads as intentional; exactly centred looks like it sank.
      int y = top + Math.max(0, Math.round((available - total) * 0.42F));
      int headerY = y;
      if (headerHeight > 0) {
         y += headerHeight + density[3];
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
      // The card takes what is left beside the column, up to its preferred width; too little room means no card.
      int cardRoom = width - CARD_MARGIN - (columnX + columnWidth + 24);
      int cardWidth = centred || cardRoom < CARD_MIN ? 0 : Math.min(CARD_MAX, cardRoom);
      int cardX = width - CARD_MARGIN - cardWidth;
      int cardBottom = footerY - Math.min(CARD_MARGIN, Math.max(6, height / 24));
      return new TitleLayout(
         width, height, centred, columnX, columnWidth, header, headerY, headerHeight, logo, logoPixels, h, slotX, slotY, slotW, linksY, density[5], footerY, cardX, cardWidth, cardBottom
      );
   }

   /** Window pixels the emblem is drawn at for a GUI scale: half its texture, or a quarter where half would be too large. */
   static int logoPixels(int scale) {
      int half = LOGO_TEXTURE / 2;
      return logoUnits(half, scale) > 44 ? LOGO_TEXTURE / 4 : half;
   }

   /** GUI units an emblem {@code pixels} window pixels wide takes at {@code scale}, rounded up; 0 when it would be too small to read. */
   static int logoUnits(int pixels, int scale) {
      int units = (pixels + scale - 1) / scale;
      return pixels <= 0 || units < MIN_LOGO ? 0 : units;
   }

   /** Height of a header variant: the taller of the emblem and the text block beside it. */
   static int headerHeight(int header, int scale) {
      if (header == HEADER_FULL) {
         return Math.max(logoUnits(logoPixels(scale), scale), NAME_CAP + TAGLINE_GAP + TAGLINE_CAP);
      }

      return header == HEADER_NAME ? NAME_CAP_SMALL + 1 : 0;
   }

   /** Height of header + menu + links for one density. */
   static int stackHeight(int[] density, int headerHeight, boolean hasContinue) {
      int rows = hasContinue ? 5 : 4;
      int menu = rows * density[0] + (hasContinue ? 2 : 1) * density[1] + 2 * density[2];
      return (headerHeight > 0 ? headerHeight + density[3] : 0) + menu + density[4] + density[5];
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

   /** Bottom edge of the whole column (the quick links). */
   public int columnBottom() {
      return this.linksY + this.linksHeight;
   }

   /** Whether the quick links have room for their icon above the caption. */
   public boolean linkTiles() {
      return this.linksHeight >= LINK_TILE;
   }

   /** Whether there is a "what's new" card. */
   public boolean hasCard() {
      return this.cardWidth > 0;
   }

   /** Left edge of quick link {@code index}: {@link #LINKS} equal tiles across the column. */
   public int linkX(int index) {
      return this.columnX + Math.round(index * (this.columnWidth + LINK_GAP) / (float)LINKS);
   }

   /** Width of quick link {@code index}; the tiles share the column exactly, so the last one ends at its right edge. */
   public int linkW(int index) {
      int next = index + 1 >= LINKS ? this.columnX + this.columnWidth + LINK_GAP : this.linkX(index + 1);
      return next - LINK_GAP - this.linkX(index);
   }

   /**
    * Left edge of the header for a header {@code groupWidth} wide (emblem, gap and name): the column's left edge,
    * or centred on the screen when the column is.
    */
   public int headerX(int groupWidth) {
      return this.centred ? Math.max(EDGE, (this.width - groupWidth) / 2) : this.columnX;
   }

   /** Top of the name's capitals: the text block (name, and in the full header the line under it) is centred in the header. */
   public int nameY() {
      int block = this.header == HEADER_FULL ? NAME_CAP + TAGLINE_GAP + TAGLINE_CAP : NAME_CAP_SMALL;
      return this.headerY + (this.headerHeight - block) / 2;
   }

   /** Top of the line under the name (full header only). */
   public int taglineY() {
      return this.nameY() + NAME_CAP + TAGLINE_GAP;
   }

   /**
    * Progress (0..1, not yet eased) of element {@code index}'s entrance {@code elapsedMs} after the screen opened:
    * the elements arrive one after the other, {@code staggerMs} apart, each taking {@code durationMs}.
    */
   public static float entrance(long elapsedMs, int index, int staggerMs, int durationMs) {
      if (durationMs <= 0) {
         return 1.0F;
      }

      float t = (elapsedMs - (long)Math.max(0, index) * staggerMs) / (float)durationMs;
      return Math.max(0.0F, Math.min(1.0F, t));
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
