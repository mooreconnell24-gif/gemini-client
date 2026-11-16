package gemini.module.modules.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * The flood fill behind Vein Miner, on plain integer coordinates so it is unit tested without a world. Starting
 * next to an origin block (which is not included: it is the block the player just broke), it collects connected
 * blocks the {@link Member} test accepts, breadth first so the nearest layers come first, and stops at a limit.
 */
public final class VeinFill {
   private VeinFill() {
   }

   @FunctionalInterface
   public interface Member {
      /** Whether the block at these coordinates belongs to the vein (same ore, in reach...). */
      boolean test(int x, int y, int z);
   }

   /**
    * Up to {@code limit} vein blocks connected to the origin, as packed coordinates ({@link #pack}). With
    * {@code diagonals} blocks touching only at an edge or corner are connected too; without, only face neighbours.
    */
   public static LongArrayList collect(int originX, int originY, int originZ, int limit, boolean diagonals, Member member) {
      LongArrayList vein = new LongArrayList();
      if (limit <= 0) {
         return vein;
      }

      LongOpenHashSet seen = new LongOpenHashSet();
      LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
      long origin = pack(originX, originY, originZ);
      seen.add(origin);
      frontier.enqueue(origin);
      while (!frontier.isEmpty() && vein.size() < limit) {
         long at = frontier.dequeueLong();
         int ax = unpackX(at);
         int ay = unpackY(at);
         int az = unpackZ(at);
         for (int dx = -1; dx <= 1 && vein.size() < limit; dx++) {
            for (int dy = -1; dy <= 1 && vein.size() < limit; dy++) {
               for (int dz = -1; dz <= 1 && vein.size() < limit; dz++) {
                  int steps = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                  if (steps == 0 || !diagonals && steps != 1) {
                     continue;
                  }

                  int x = ax + dx;
                  int y = ay + dy;
                  int z = az + dz;
                  long next = pack(x, y, z);
                  if (seen.add(next) && member.test(x, y, z)) {
                     vein.add(next);
                     frontier.enqueue(next);
                  }
               }
            }
         }
      }

      return vein;
   }

   /** Packs coordinates like {@code BlockPos.asLong}: 26 bits x, 12 bits y, 26 bits z. */
   public static long pack(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
   }

   public static int unpackX(long packed) {
      return (int)(packed >> 38);
   }

   public static int unpackY(long packed) {
      return (int)(packed << 52 >> 52);
   }

   public static int unpackZ(long packed) {
      return (int)(packed << 26 >> 38);
   }
}
