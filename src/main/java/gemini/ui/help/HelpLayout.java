package gemini.ui.help;

import gemini.ui.Theme;

/**
 * Where the parts of the help screen go for a given GUI size. Pure arithmetic, tested at GUI scales 1 to 4: the
 * card fills small windows (keeping {@link Theme#EDGE} to the screen edge) and stops growing at a readable line
 * length; the tab strip switches to short labels and then to equal shares when the full labels do not fit.
 */
public record HelpLayout(
   int panelX,
   int panelY,
   int panelWidth,
   int panelHeight,
   int tabsY,
   int[] tabX,
   int[] tabWidth,
   boolean shortTabs,
   int contentX,
   int contentY,
   int contentWidth,
   int contentHeight,
   int footerY
) {
   public static final int MAX_WIDTH = 460;
   public static final int MAX_HEIGHT = 340;
   public static final int HEADER_HEIGHT = 22;
   public static final int TAB_HEIGHT = 14;
   public static final int FOOTER_HEIGHT = Theme.BUTTON_HEIGHT_SMALL + Theme.SPACE_L;
   private static final int TAB_GAP = 3;
   private static final int TAB_PAD = 12;

   public static HelpLayout compute(int width, int height, int[] fullTabWidths, int[] shortTabWidths) {
      int pw = Math.max(120, Math.min(MAX_WIDTH, width - 2 * Theme.EDGE));
      int ph = Math.max(100, Math.min(MAX_HEIGHT, height - 2 * Theme.EDGE));
      int px = (width - pw) / 2;
      int py = (height - ph) / 2;
      int count = fullTabWidths.length;
      int inner = pw - 2 * Theme.SPACE_L;
      int full = 0;
      int brief = 0;
      for (int i = 0; i < count; i++) {
         full += fullTabWidths[i] + TAB_PAD;
         brief += shortTabWidths[i] + TAB_PAD;
      }

      int gaps = Math.max(0, count - 1) * TAB_GAP;
      boolean useShort = full + gaps > inner;
      int[] tabWidth = new int[count];
      int[] tabX = new int[count];
      boolean share = useShort && brief + gaps > inner;
      int cursor = px + Theme.SPACE_L;
      for (int i = 0; i < count; i++) {
         tabWidth[i] = share ? (inner - gaps) / Math.max(1, count) : (useShort ? shortTabWidths[i] : fullTabWidths[i]) + TAB_PAD;
         tabX[i] = cursor;
         cursor += tabWidth[i] + TAB_GAP;
      }

      int tabsY = py + HEADER_HEIGHT;
      int contentY = tabsY + TAB_HEIGHT + Theme.SPACE_M;
      int footerY = py + ph - FOOTER_HEIGHT;
      int contentX = px + Theme.SPACE_L;
      int contentWidth = pw - 2 * Theme.SPACE_L - Theme.SPACE_S; // room for the scrollbar
      int contentHeight = Math.max(0, footerY - Theme.SPACE_XS - contentY);
      return new HelpLayout(px, py, pw, ph, tabsY, tabX, tabWidth, useShort, contentX, contentY, contentWidth, contentHeight, footerY);
   }
}
