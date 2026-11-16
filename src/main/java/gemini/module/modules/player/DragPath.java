package gemini.module.modules.player;

/**
 * Which inventory slots a mouse drag passed over between two ticks. The mouse can cross several slots in one
 * tick, so the segment between the two positions is walked in small steps rather than only its end checked.
 * Slots are 16x16 squares given by their top-left corners; like vanilla's hover test, a slot also counts one
 * pixel beyond its edges, so a drag along the 2px gap between two slots still picks them up.
 */
public final class DragPath {
   /** Distance between samples; smaller than the 2px gap between neighbouring slots plus the 1px hover margins. */
   private static final double STEP = 1.5;

   private DragPath() {
   }

   /** The index into {@code slotX}/{@code slotY} of the slot under a point, or -1. */
   public static int slotAt(double x, double y, int[] slotX, int[] slotY, int count) {
      for (int i = 0; i < count; i++) {
         if (x >= slotX[i] - 1 && x < slotX[i] + 17 && y >= slotY[i] - 1 && y < slotY[i] + 17) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Writes the slots crossed from (x0, y0) to (x1, y1) into {@code out} in the order they were reached, each once,
    * and returns how many there are (at most {@code out.length}). The start point counts as crossed.
    */
   public static int crossed(double x0, double y0, double x1, double y1, int[] slotX, int[] slotY, int count, int[] out) {
      double length = Math.hypot(x1 - x0, y1 - y0);
      int steps = Math.max(1, (int)Math.ceil(length / STEP));
      int found = 0;
      for (int step = 0; step <= steps && found < out.length; step++) {
         double t = (double)step / steps;
         int slot = slotAt(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, slotX, slotY, count);
         if (slot >= 0 && !contains(out, found, slot)) {
            out[found++] = slot;
         }
      }

      return found;
   }

   private static boolean contains(int[] values, int count, int value) {
      for (int i = 0; i < count; i++) {
         if (values[i] == value) {
            return true;
         }
      }

      return false;
   }
}
