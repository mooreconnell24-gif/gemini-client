package gemini.ui;

import java.util.OptionalDouble;

/**
 * The arithmetic behind the menus that needs no running game: magnetic snapping for dragged panels and HUD
 * widgets, slider dragging with a precision mode, inertial scrolling and parsing a typed number. Everything is
 * static and free of Minecraft classes, so it is covered by plain unit tests.
 */
public final class UiMath {
   /** Nothing was close and no grid was asked for: the position is unchanged. */
   public static final int SNAP_NONE = 0;
   /** Rounded to the alignment grid. */
   public static final int SNAP_GRID = 1;
   /** Pulled to a screen edge (inset by the margin). */
   public static final int SNAP_EDGE = 2;
   /** Centred on the screen. */
   public static final int SNAP_CENTER = 3;
   /** An edge or the centre lines up with the same edge or centre of another element. */
   public static final int SNAP_ALIGN = 4;
   /** Butted up against another element with the standard gap. */
   public static final int SNAP_GAP = 5;
   /** Spaced so the gap to a neighbour equals the gap between two other elements. */
   public static final int SNAP_EQUAL = 6;

   private UiMath() {
   }

   /** Where one axis of a dragged element ended up, and the guide line to draw for it. */
   public static final class AxisSnap {
      /** The start coordinate (left or top) the element should take. */
      public float pos;
      /** The coordinate of the guide line, or NaN when nothing magnetic was hit. */
      public float line = Float.NaN;
      /** One of the {@code SNAP_*} constants. */
      public int kind = SNAP_NONE;

      public boolean magnetic() {
         return this.kind >= SNAP_EDGE;
      }
   }

   /**
    * Snaps one axis of a dragged element. The closest magnetic target within {@code magnet} wins: the screen
    * edges and centre (when {@code screenTargets}), then for every other element its start, end and centre
    * lines and the two positions beside it with {@code gap} between, then positions that repeat the spacing
    * between two other elements (when {@code equalGaps}). When nothing is close the position is rounded to
    * {@code grid} (pass 0 for no grid).
    *
    * @param pos      wanted start coordinate of the dragged element
    * @param size     its extent along this axis
    * @param screen   screen extent along this axis
    * @param margin   inset kept from the screen edges
    * @param starts   start coordinates of the other elements (first {@code count} entries are used)
    * @param sizes    extents of the other elements
    */
   public static AxisSnap snapAxis(
      float pos,
      float size,
      float screen,
      float margin,
      boolean screenTargets,
      float[] starts,
      float[] sizes,
      int count,
      float magnet,
      float gap,
      float grid,
      boolean equalGaps
   ) {
      AxisSnap result = new AxisSnap();
      result.pos = pos;
      float best = magnet;
      if (screenTargets) {
         best = offer(result, pos, margin, margin, SNAP_EDGE, best);
         best = offer(result, pos, screen - margin - size, screen - margin, SNAP_EDGE, best);
         best = offer(result, pos, (screen - size) / 2.0F, screen / 2.0F, SNAP_CENTER, best);
      }

      int n = starts == null || sizes == null ? 0 : Math.min(count, Math.min(starts.length, sizes.length));
      for (int i = 0; i < n; i++) {
         float start = starts[i];
         float end = start + sizes[i];
         float middle = start + sizes[i] / 2.0F;
         best = offer(result, pos, start, start, SNAP_ALIGN, best);
         best = offer(result, pos, end - size, end, SNAP_ALIGN, best);
         best = offer(result, pos, middle - size / 2.0F, middle, SNAP_ALIGN, best);
         best = offer(result, pos, end + gap, end + gap, SNAP_GAP, best);
         best = offer(result, pos, start - gap - size, start - gap, SNAP_GAP, best);
      }

      if (equalGaps) {
         for (int i = 0; i < n; i++) {
            float endI = starts[i] + sizes[i];
            for (int j = 0; j < n; j++) {
               float spacing = starts[j] - endI;
               if (i == j || spacing <= 0.5F) {
                  continue;
               }

               float endJ = starts[j] + sizes[j];
               // continue the row after the second element, before the first, or sit centred between them
               best = offer(result, pos, endJ + spacing, endJ + spacing, SNAP_EQUAL, best);
               best = offer(result, pos, starts[i] - spacing - size, starts[i] - spacing, SNAP_EQUAL, best);
               if (spacing > size) {
                  best = offer(result, pos, endI + (spacing - size) / 2.0F, endI + spacing / 2.0F, SNAP_EQUAL, best);
               }
            }
         }
      }

      if (result.kind == SNAP_NONE && grid > 0.0F) {
         result.pos = Math.round(pos / grid) * grid;
         result.kind = SNAP_GRID;
      }

      return result;
   }

   private static float offer(AxisSnap result, float pos, float target, float line, int kind, float best) {
      float distance = Math.abs(pos - target);
      if (distance < best) {
         result.pos = target;
         result.line = line;
         result.kind = kind;
         return distance;
      }

      return best;
   }

   /**
    * The slider fraction (0..1) after one mouse-drag step. Normally the knob follows the cursor; in precise mode
    * (Shift held) it moves relative to where it is, at a tenth of the cursor's speed, so it never jumps.
    *
    * @param percent    the fraction before this step (the unrounded drag position, not the stepped value)
    * @param lastMouse  cursor coordinate at the previous step
    * @param mouse      cursor coordinate now
    * @param trackStart coordinate of the track's start
    * @param trackSize  length of the track
    */
   public static double sliderDrag(double percent, double lastMouse, double mouse, double trackStart, double trackSize, boolean precise) {
      double size = Math.max(1.0, trackSize);
      double next = precise ? percent + (mouse - lastMouse) / size * 0.1 : (mouse - trackStart) / size;
      return Math.max(0.0, Math.min(1.0, next));
   }

   /**
    * Reads a number someone typed into a slider. Only plain decimals are accepted, such as "-2", "0.25", ".5",
    * "+4" or "3,5" (decimal comma), with spaces around them ignored; exponents, hex and suffixes are not. Empty
    * when the text is not such a number.
    */
   public static OptionalDouble parseNumber(String text) {
      if (text == null) {
         return OptionalDouble.empty();
      }

      String clean = text.trim().replace(',', '.');
      if (clean.startsWith("+")) {
         clean = clean.substring(1);
         if (clean.startsWith("-")) {
            return OptionalDouble.empty();
         }
      }

      if (clean.isEmpty() || clean.length() > 24) {
         return OptionalDouble.empty();
      }

      boolean digit = false;
      boolean dot = false;
      for (int i = 0; i < clean.length(); i++) {
         char c = clean.charAt(i);
         if (c >= '0' && c <= '9') {
            digit = true;
         } else if (c == '.' && !dot) {
            dot = true;
         } else if (c != '-' || i != 0) {
            return OptionalDouble.empty();
         }
      }

      if (!digit) {
         return OptionalDouble.empty();
      }

      try {
         double value = Double.parseDouble(clean);
         return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
      } catch (NumberFormatException e) {
         return OptionalDouble.empty();
      }
   }

   /**
    * Position of a gliding scroll after {@code dt} seconds: the velocity decays exponentially with
    * {@code friction}, so the result is the same whatever the frame rate. A glide started with velocity
    * {@code distance * friction} travels exactly {@code distance} before it stops.
    */
   public static float glidePosition(float pos, float velocity, float friction, float dt) {
      if (friction <= 0.0F) {
         return pos + velocity * Math.max(0.0F, dt);
      }

      return pos + velocity * (1.0F - (float)Math.exp(-friction * Math.max(0.0F, dt))) / friction;
   }

   /** Velocity of the same glide after {@code dt} seconds; drops to exactly 0 once it is imperceptible. */
   public static float glideVelocity(float velocity, float friction, float dt) {
      float next = velocity * (float)Math.exp(-friction * Math.max(0.0F, dt));
      return Math.abs(next) < 0.5F ? 0.0F : next;
   }
}
