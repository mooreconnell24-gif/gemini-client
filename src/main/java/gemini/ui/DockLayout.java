package gemini.ui;

/**
 * Placement of the Gemini bar on the pause menu. Pure arithmetic so it can be tested without a running game.
 *
 * <p>The vanilla pause menu is a column in the middle of the screen with its title at y 40. The bar is one slim
 * pill centred along the top edge, above that title, so it never touches a vanilla button: a row of entries, each
 * an icon and a label. When the full labels do not fit the window it uses the short ones, and when those do not
 * fit either, the icons alone. The panel fields are the pill; the entries sit inside it, {@link #INSET} from its
 * edge.
 */
public record DockLayout(int mode, int panelX, int panelY, int panelWidth, int panelHeight, int[] x, int[] y, int[] w, int buttonHeight) {
   /** Label modes, in order of preference. */
   public static final int FULL = 0;
   public static final int SHORT = 1;
   public static final int ICONS = 2;
   /** The vanilla "Game Menu" title sits at this y; the bar has to end above it. */
   public static final int VANILLA_TITLE_Y = 40;
   /** Height of the pill and of an entry inside it. */
   public static final int BAR_HEIGHT = 24;
   public static final int BUTTON_HEIGHT = 18;
   /** Distance between the pill's edge and its entries, and the corner radius that goes with it. */
   public static final int INSET = (BAR_HEIGHT - BUTTON_HEIGHT) / 2;
   public static final int RADIUS = BAR_HEIGHT / 2;
   /** Distance of the pill from the top of the screen. */
   public static final int TOP = 7;
   /** Side of an entry's icon and the gap between it and the label. */
   public static final int ICON = GlassIcon.SIZE;
   public static final int ICON_GAP = 5;
   private static final int EDGE = 6;
   private static final int GAP = 2;
   private static final int PAD = 8;

   /**
    * @param fullWidths text width of each full label
    * @param shortWidths text width of each short label
    */
   public static DockLayout compute(int screenWidth, int screenHeight, int[] fullWidths, int[] shortWidths) {
      int count = fullWidths.length;
      int available = Math.max(count, screenWidth - 2 * EDGE - 2 * INSET - Math.max(0, count - 1) * GAP);
      int mode = FULL;
      int[] w = widths(fullWidths, FULL);
      if (sum(w) > available) {
         mode = SHORT;
         w = widths(shortWidths, SHORT);
      }

      if (sum(w) > available) {
         mode = ICONS;
         w = widths(shortWidths, ICONS);
         if (sum(w) > available) {
            // Narrower than even the icons need: an equal share each.
            for (int i = 0; i < count; i++) {
               w[i] = Math.max(1, available / Math.max(1, count));
            }
         }
      }

      int inner = sum(w) + Math.max(0, count - 1) * GAP;
      int panelWidth = inner + 2 * INSET;
      int panelX = (screenWidth - panelWidth) / 2;
      int[] x = new int[count];
      int[] y = new int[count];
      int cursor = panelX + INSET;
      for (int i = 0; i < count; i++) {
         x[i] = cursor;
         y[i] = TOP + INSET;
         cursor += w[i] + GAP;
      }

      return new DockLayout(mode, panelX, TOP, panelWidth, BAR_HEIGHT, x, y, w, BUTTON_HEIGHT);
   }

   private static int[] widths(int[] labels, int mode) {
      int[] w = new int[labels.length];
      for (int i = 0; i < labels.length; i++) {
         w[i] = mode == ICONS ? ICON + 2 * INSET + 2 : 2 * PAD + ICON + ICON_GAP + labels[i];
      }

      return w;
   }

   private static int sum(int[] values) {
      int total = 0;
      for (int value : values) {
         total += value;
      }

      return total;
   }

   /** Whether the entries show the short labels. */
   public boolean shortLabels() {
      return this.mode == SHORT;
   }

   /** Whether the entries show only their icons. */
   public boolean iconsOnly() {
      return this.mode == ICONS;
   }
}
