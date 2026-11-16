package gemini.module.modules.render;

/**
 * Turns a grid of biome ids into the lines where the biome changes. Cell (i, j) of a {@code width x depth} grid is
 * {@code ids[j * width + i]}, i along X and j along Z; a negative id means unknown (not loaded) and never makes a
 * border. Borders are emitted as {@code x1, z1, x2, z2} in cell-corner units, with neighbouring pieces on the same
 * line merged so a straight border is one segment instead of one per cell.
 */
public final class BiomeEdges {
   private BiomeEdges() {
   }

   /**
    * Writes the border segments into {@code out} (four ints each) and returns how many segments there are. Stops
    * early, keeping what it has, when {@code out} is full.
    */
   public static int compute(int[] ids, int width, int depth, int[] out) {
      int count = 0;
      int capacity = out.length / 4;
      // Lines of constant X between column i and i + 1, running along Z.
      for (int i = 0; i + 1 < width && count < capacity; i++) {
         int start = -1;
         for (int j = 0; j <= depth && count < capacity; j++) {
            boolean edge = j < depth && differs(ids[j * width + i], ids[j * width + i + 1]);
            if (edge && start < 0) {
               start = j;
            } else if (!edge && start >= 0) {
               count = put(out, count, i + 1, start, i + 1, j);
               start = -1;
            }
         }
      }

      // Lines of constant Z between row j and j + 1, running along X.
      for (int j = 0; j + 1 < depth && count < capacity; j++) {
         int start = -1;
         for (int i = 0; i <= width && count < capacity; i++) {
            boolean edge = i < width && differs(ids[j * width + i], ids[(j + 1) * width + i]);
            if (edge && start < 0) {
               start = i;
            } else if (!edge && start >= 0) {
               count = put(out, count, start, j + 1, i, j + 1);
               start = -1;
            }
         }
      }

      return count;
   }

   private static boolean differs(int a, int b) {
      return a >= 0 && b >= 0 && a != b;
   }

   private static int put(int[] out, int count, int x1, int z1, int x2, int z2) {
      int base = count * 4;
      out[base] = x1;
      out[base + 1] = z1;
      out[base + 2] = x2;
      out[base + 3] = z2;
      return count + 1;
   }
}
