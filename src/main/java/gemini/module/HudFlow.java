package gemini.module;

import java.util.Arrays;

/**
 * Automatic placement for the HUD widgets the user has not moved themselves. Pure integer maths, no game classes,
 * so it is unit tested.
 *
 * <p>Usage per frame: {@link #begin}, {@link #reserve} every area that is already taken (the vanilla HUD, the
 * watermark, widgets the user placed), {@link #add} every widget that should flow, {@link #solve}, then read
 * {@link #x}/{@link #y}.
 *
 * <p>The rule: each widget belongs to one of eight stacks, named like the anchors ({@link #TOP_LEFT} ...). Stacks
 * are filled in a fixed order (top and bottom centre, then the four corners, then the side centres) and inside a
 * stack by the widget's order number, then by the order it was added. Each widget takes the free spot closest to
 * its stack's start that keeps at least {@link #gap} pixels to everything already taken:
 * <ul>
 *   <li>corner and side stacks are columns: they grow away from their edge (down from the top, up from the
 *       bottom, down from the middle for the side centres) and open a new column further in when the first one is
 *       full;</li>
 *   <li>the top and bottom centre stacks are rows: they fill sideways around the centre first and open a new row
 *       further in (down from the top, up from the bottom) when that row is full.</li>
 * </ul>
 * Corner and side columns stay on their own side of the screen and centre rows in the middle half. When that
 * leaves no room, any free spot on the screen is taken; a widget that fits nowhere at all (the screen is genuinely
 * full) goes to its stack's start and {@link #fitted} says so.
 */
public final class HudFlow {
   public static final int TOP_LEFT = 0;
   public static final int TOP_RIGHT = 1;
   public static final int BOTTOM_LEFT = 2;
   public static final int BOTTOM_RIGHT = 3;
   public static final int TOP_CENTER = 4;
   public static final int BOTTOM_CENTER = 5;
   public static final int LEFT_CENTER = 6;
   public static final int RIGHT_CENTER = 7;
   /** The names the widgets' Corner setting uses, indexed by the constants above. */
   private static final String[] NAMES = {
      "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Top Center", "Bottom Center", "Left Center", "Right Center"
   };
   public static final int DEFAULT_GAP = 3;
   /** Fill order of the stacks: the two centre rows first (they are narrow), then the corners, then the side centres. */
   private static final int[] RANK = {2, 3, 4, 5, 0, 1, 6, 7};
   /** Second pass for a crowded screen: any free spot, whatever the stack's direction. */
   private boolean relaxed;
   private int width = 1;
   private int height = 1;
   private int gap = DEFAULT_GAP;
   /** Taken rectangles, four ints each: x, y, w, h. */
   private int[] taken = new int[64];
   private int takenCount;
   private int[] anchor = new int[16];
   private int[] order = new int[16];
   private int[] itemW = new int[16];
   private int[] itemH = new int[16];
   private int[] inset = new int[16];
   private int[] outX = new int[16];
   private int[] outY = new int[16];
   private boolean[] fitted = new boolean[16];
   private int[] sorted = new int[16];
   private int itemCount;
   private final int[] stackHeight = new int[NAMES.length];
   private final int[] stackItems = new int[NAMES.length];
   private int[] candX = new int[32];
   private int[] candY = new int[32];

   public static int anchor(String name) {
      for (int i = 0; i < NAMES.length; i++) {
         if (NAMES[i].equals(name)) {
            return i;
         }
      }

      return TOP_LEFT;
   }

   public static String anchorName(int anchor) {
      return NAMES[Math.floorMod(anchor, NAMES.length)];
   }

   private static boolean leftSide(int a) {
      return a == TOP_LEFT || a == BOTTOM_LEFT || a == LEFT_CENTER;
   }

   private static boolean rightSide(int a) {
      return a == TOP_RIGHT || a == BOTTOM_RIGHT || a == RIGHT_CENTER;
   }

   private static boolean topSide(int a) {
      return a == TOP_LEFT || a == TOP_RIGHT || a == TOP_CENTER;
   }

   private static boolean bottomSide(int a) {
      return a == BOTTOM_LEFT || a == BOTTOM_RIGHT || a == BOTTOM_CENTER;
   }

   /** Starts a new layout for a screen of this size (in HUD pixels). */
   public void begin(int width, int height) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.takenCount = 0;
      this.itemCount = 0;
   }

   /** Minimum distance kept between two widgets, and between a widget and a reserved area. */
   public void setGap(int gap) {
      this.gap = Math.max(0, gap);
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   /** Marks an area as taken; empty rectangles are ignored. */
   public void reserve(int x, int y, int w, int h) {
      if (w <= 0 || h <= 0) {
         return;
      }

      int at = this.takenCount * 4;
      if (at + 4 > this.taken.length) {
         this.taken = Arrays.copyOf(this.taken, this.taken.length * 2);
      }

      this.taken[at] = x;
      this.taken[at + 1] = y;
      this.taken[at + 2] = w;
      this.taken[at + 3] = h;
      this.takenCount++;
   }

   public int reservedCount() {
      return this.takenCount;
   }

   /**
    * Adds a widget to a stack and returns its index for {@link #x}/{@link #y}. {@code inset} is its distance from the
    * screen edge at the start of the stack; lower {@code order} comes first in the stack.
    */
   public int add(int anchor, int order, int w, int h, int inset) {
      int i = this.itemCount;
      if (i == this.anchor.length) {
         int size = i * 2;
         this.anchor = Arrays.copyOf(this.anchor, size);
         this.order = Arrays.copyOf(this.order, size);
         this.itemW = Arrays.copyOf(this.itemW, size);
         this.itemH = Arrays.copyOf(this.itemH, size);
         this.inset = Arrays.copyOf(this.inset, size);
         this.outX = Arrays.copyOf(this.outX, size);
         this.outY = Arrays.copyOf(this.outY, size);
         this.fitted = Arrays.copyOf(this.fitted, size);
         this.sorted = Arrays.copyOf(this.sorted, size);
      }

      this.anchor[i] = Math.floorMod(anchor, NAMES.length);
      this.order[i] = order;
      this.itemW[i] = Math.max(1, w);
      this.itemH[i] = Math.max(1, h);
      this.inset[i] = Math.max(0, inset);
      this.outX[i] = 0;
      this.outY[i] = 0;
      this.fitted[i] = false;
      this.itemCount++;
      return i;
   }

   public int x(int index) {
      return this.outX[index];
   }

   public int y(int index) {
      return this.outY[index];
   }

   /** False when the widget did not fit anywhere and was put at its stack's start, overlapping something. */
   public boolean fitted(int index) {
      return this.fitted[index];
   }

   private boolean before(int a, int b) {
      if (this.anchor[a] != this.anchor[b]) {
         return RANK[this.anchor[a]] < RANK[this.anchor[b]];
      }

      return this.order[a] != this.order[b] ? this.order[a] < this.order[b] : a < b;
   }

   public void solve() {
      int n = this.itemCount;
      for (int i = 0; i < n; i++) {
         int j = i;
         while (j > 0 && this.before(i, this.sorted[j - 1])) {
            this.sorted[j] = this.sorted[j - 1];
            j--;
         }

         this.sorted[j] = i;
      }

      Arrays.fill(this.stackHeight, 0);
      Arrays.fill(this.stackItems, 0);
      for (int i = 0; i < n; i++) {
         int a = this.anchor[i];
         this.stackHeight[a] += this.itemH[i] + (this.stackItems[a] > 0 ? this.gap : 0);
         this.stackItems[a]++;
      }

      for (int k = 0; k < n; k++) {
         int i = this.sorted[k];
         this.place(i);
         this.reserve(this.outX[i], this.outY[i], this.itemW[i], this.itemH[i]);
      }
   }

   private void place(int i) {
      int a = this.anchor[i];
      int w = this.itemW[i];
      int h = this.itemH[i];
      int maxX = Math.max(0, this.width - w);
      int maxY = Math.max(0, this.height - h);
      int x0;
      if (leftSide(a)) {
         x0 = this.inset[i];
      } else if (rightSide(a)) {
         x0 = this.width - this.inset[i] - w;
      } else {
         x0 = (this.width - w) / 2;
      }

      int y0;
      if (topSide(a)) {
         y0 = this.inset[i];
      } else if (bottomSide(a)) {
         y0 = this.height - this.inset[i] - h;
      } else {
         // The side-centre stacks are centred as a whole and grow downward.
         y0 = Math.max(this.inset[i], (this.height - this.stackHeight[a]) / 2);
      }

      x0 = clamp(x0, 0, maxX);
      y0 = clamp(y0, 0, maxY);
      this.relaxed = false;
      if (this.search(i, a, x0, y0, w, h, maxX, maxY)) {
         return;
      }

      this.relaxed = true;
      boolean found = this.search(i, a, x0, y0, w, h, maxX, maxY);
      this.relaxed = false;
      if (found) {
         return;
      }

      this.outX[i] = x0;
      this.outY[i] = y0;
      this.fitted[i] = false;
   }

   private boolean search(int i, int a, int x0, int y0, int w, int h, int maxX, int maxY) {
      int xs = this.candidates(true, a, x0, w, maxX);
      int ys = this.candidates(false, a, y0, h, maxY);
      boolean rows = a == TOP_CENTER || a == BOTTOM_CENTER;
      int outer = rows ? ys : xs;
      int inner = rows ? xs : ys;
      for (int p = 0; p < outer; p++) {
         for (int q = 0; q < inner; q++) {
            int x = rows ? this.candX[q] : this.candX[p];
            int y = rows ? this.candY[p] : this.candY[q];
            if (this.free(x, y, w, h)) {
               this.outX[i] = x;
               this.outY[i] = y;
               this.fitted[i] = true;
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Fills {@link #candX} or {@link #candY} with the start position and every position touching a taken area, keeps
    * those inside the screen and on the stack's growing side, sorts them nearest first and returns how many.
    */
   private int candidates(boolean horizontal, int a, int start, int size, int max) {
      int needed = this.takenCount * 2 + 1;
      int[] out = horizontal ? this.candX : this.candY;
      if (out.length < needed) {
         out = new int[Math.max(needed, out.length * 2)];
         if (horizontal) {
            this.candX = out;
         } else {
            this.candY = out;
         }
      }

      int count = 0;
      out[count++] = start;
      int offset = horizontal ? 0 : 1;
      for (int t = 0; t < this.takenCount; t++) {
         int pos = this.taken[t * 4 + offset];
         int len = this.taken[t * 4 + offset + 2];
         int after = pos + len + this.gap;
         int beforePos = pos - this.gap - size;
         if (this.allowed(horizontal, a, start, after, max)) {
            out[count++] = after;
         }

         if (this.allowed(horizontal, a, start, beforePos, max)) {
            out[count++] = beforePos;
         }
      }

      // insertion sort by cost, then drop duplicates
      for (int p = 1; p < count; p++) {
         int value = out[p];
         long cost = this.cost(horizontal, a, start, value);
         int q = p;
         while (q > 0 && (this.cost(horizontal, a, start, out[q - 1]) > cost
            || this.cost(horizontal, a, start, out[q - 1]) == cost && out[q - 1] > value)) {
            out[q] = out[q - 1];
            q--;
         }

         out[q] = value;
      }

      int unique = 0;
      for (int p = 0; p < count; p++) {
         if (unique == 0 || out[unique - 1] != out[p]) {
            out[unique++] = out[p];
         }
      }

      return unique;
   }

   private boolean allowed(boolean horizontal, int a, int start, int value, int max) {
      if (value < 0 || value > max) {
         return false;
      }

      if (this.relaxed) {
         return true;
      }

      int size = this.width - max;
      if (horizontal && leftSide(a) && value + size > this.width / 2 + this.width / 8) {
         // left columns stay on the left part of the screen
         return false;
      }

      if (horizontal && rightSide(a) && value < this.width / 2 - this.width / 8) {
         return false;
      }

      if (horizontal && (a == TOP_CENTER || a == BOTTOM_CENTER)) {
         // centre rows stay in the middle half of the screen instead of drifting into the corners' columns
         int centre = value + (this.width - max) / 2;
         return Math.abs(centre - this.width / 2) <= this.width / 4;
      }

      if (horizontal) {
         return leftSide(a) ? value >= start : (!rightSide(a) || value <= start);
      } else {
         return topSide(a) ? value >= start : (!bottomSide(a) || value <= start);
      }
   }

   /** How far a candidate is from the stack's start; positions against the growing direction cost extra. */
   private long cost(boolean horizontal, int a, int start, int value) {
      int distance = Math.abs(value - start);
      if (!horizontal && (a == LEFT_CENTER || a == RIGHT_CENTER) && value < start) {
         // side-centre stacks grow down first and only go up when there is no room below
         return (long)distance + this.height;
      }

      return distance;
   }

   private boolean free(int x, int y, int w, int h) {
      int g = this.gap;
      for (int t = 0; t < this.takenCount; t++) {
         int at = t * 4;
         int tx = this.taken[at];
         int ty = this.taken[at + 1];
         int tw = this.taken[at + 2];
         int th = this.taken[at + 3];
         if (x < tx + tw + g && tx < x + w + g && y < ty + th + g && ty < y + h + g) {
            return false;
         }
      }

      return true;
   }

   private static int clamp(int value, int min, int max) {
      return value < min ? min : Math.min(value, max);
   }
}
