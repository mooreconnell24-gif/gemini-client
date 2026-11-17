package gemini.module.modules.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a walking target will be over the next few ticks, as block positions. Free of game classes so it can be
 * unit tested: the world is asked through {@link Walkable}.
 *
 * <p>The target keeps its horizontal velocity, slowed each tick by ground friction. Each block it enters must be
 * walkable; a step of one block up or a drop of up to two is followed, anything else (a wall, a cliff) ends the
 * path where it is. Each block appears once, in the order it is reached.
 */
public final class WebPath {
   /** Vanilla ground drag for an ordinary block: 0.91 air drag times 0.6 block friction. */
   public static final double GROUND_DRAG = 0.546;
   /** Below this speed (blocks per tick) the target is treated as standing still. */
   static final double STILL = 0.003;

   private WebPath() {
   }

   /** Whether an entity could stand with its feet in this block. */
   @FunctionalInterface
   public interface Walkable {
      boolean test(int x, int y, int z);
   }

   /**
    * The blocks the target passes through, starting with the one it stands in.
    *
    * @param drag how much of the velocity is kept per tick (1 keeps it all)
    * @return a list of {x, y, z} triples
    */
   public static List<int[]> predict(double x, double y, double z, double vx, double vz, int ticks, double drag, Walkable walkable) {
      List<int[]> path = new ArrayList<>();
      int bx = floor(x);
      int by = floor(y);
      int bz = floor(z);
      path.add(new int[]{bx, by, bz});
      double px = x;
      double pz = z;
      for (int tick = 0; tick < ticks; tick++) {
         if (Math.abs(vx) < STILL && Math.abs(vz) < STILL) {
            break;
         }

         px += vx;
         pz += vz;
         vx *= drag;
         vz *= drag;
         int nx = floor(px);
         int nz = floor(pz);
         if (nx == bx && nz == bz) {
            continue;
         }

         int ny = settle(nx, by, nz, walkable);
         if (ny == Integer.MIN_VALUE) {
            break;
         }

         bx = nx;
         by = ny;
         bz = nz;
         path.add(new int[]{bx, by, bz});
      }

      return path;
   }

   /** The height the target ends up at in column (x, z) coming from {@code y}, or MIN_VALUE when it cannot go there. */
   static int settle(int x, int y, int z, Walkable walkable) {
      if (walkable.test(x, y, z)) {
         return y;
      } else if (walkable.test(x, y + 1, z)) {
         return y + 1;
      }

      for (int drop = 1; drop <= 2; drop++) {
         if (walkable.test(x, y - drop, z)) {
            return y - drop;
         }
      }

      return Integer.MIN_VALUE;
   }

   private static int floor(double value) {
      return (int)Math.floor(value);
   }
}
