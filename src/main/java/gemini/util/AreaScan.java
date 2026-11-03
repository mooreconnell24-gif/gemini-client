package gemini.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;

/**
 * Walks a box of block positions a little at a time. Modules that used to scan their whole radius in one tick
 * (tens of thousands of block reads, a visible hitch) call {@link #tick} every tick with a time budget instead; the
 * walk resumes where it stopped and {@link #tick} returns true once the whole box has been visited, which is when
 * the caller swaps in the results it collected.
 */
public final class AreaScan {
   private final MutableBlockPos cursor = new MutableBlockPos();
   private int minX;
   private int minY;
   private int minZ;
   private int maxX;
   private int maxY;
   private int maxZ;
   private int x;
   private int y;
   private int z;
   private boolean running;

   /** Starts (or restarts) a walk over the inclusive box. */
   public void start(BlockPos min, BlockPos max) {
      this.minX = Math.min(min.getX(), max.getX());
      this.minY = Math.min(min.getY(), max.getY());
      this.minZ = Math.min(min.getZ(), max.getZ());
      this.maxX = Math.max(min.getX(), max.getX());
      this.maxY = Math.max(min.getY(), max.getY());
      this.maxZ = Math.max(min.getZ(), max.getZ());
      this.x = this.minX;
      this.y = this.minY;
      this.z = this.minZ;
      this.running = true;
   }

   public boolean running() {
      return this.running;
   }

   public void stop() {
      this.running = false;
   }

   /**
    * Visits positions until {@code budgetNanos} has elapsed or the box is done. Returns true when the box was
    * finished during this call. Positions are visited x-major, then z, then y, so a horizontal layer completes first.
    */
   public boolean tick(long budgetNanos, AreaScan.Visitor visitor) {
      if (!this.running) {
         return false;
      }

      long deadline = System.nanoTime() + budgetNanos;
      int steps = 0;
      while (true) {
         this.cursor.set(this.x, this.y, this.z);
         if (!visitor.visit(this.cursor)) {
            this.running = false;
            return true;
         }

         if (++this.z > this.maxZ) {
            this.z = this.minZ;
            if (++this.x > this.maxX) {
               this.x = this.minX;
               if (++this.y > this.maxY) {
                  this.running = false;
                  return true;
               }
            }
         }

         // check the clock every 64 blocks; nanoTime itself is not free
         if ((++steps & 63) == 0 && System.nanoTime() >= deadline) {
            return false;
         }
      }
   }

   @FunctionalInterface
   public interface Visitor {
      /** Return false to stop the whole scan early (enough results). */
      boolean visit(MutableBlockPos pos);
   }
}
