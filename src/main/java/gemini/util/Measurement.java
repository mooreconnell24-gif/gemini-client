package gemini.util;

import java.util.Locale;

/**
 * The distance and box between two blocks, for the Measure module. Distances run between block centres; sizes count
 * blocks inclusively, so two blocks side by side are 1 m apart and the box between them is 2 x 1 x 1. Labels are built
 * once per measurement, never per frame.
 */
public final class Measurement {
   public final int ax;
   public final int ay;
   public final int az;
   public final int bx;
   public final int by;
   public final int bz;
   private String distanceLabel;
   private String sizeLabel;
   private String label;

   public Measurement(int ax, int ay, int az, int bx, int by, int bz) {
      this.ax = ax;
      this.ay = ay;
      this.az = az;
      this.bx = bx;
      this.by = by;
      this.bz = bz;
   }

   public boolean same(int ax, int ay, int az, int bx, int by, int bz) {
      return this.ax == ax && this.ay == ay && this.az == az && this.bx == bx && this.by == by && this.bz == bz;
   }

   public int sizeX() {
      return Math.abs(this.bx - this.ax) + 1;
   }

   public int sizeY() {
      return Math.abs(this.by - this.ay) + 1;
   }

   public int sizeZ() {
      return Math.abs(this.bz - this.az) + 1;
   }

   /** Straight-line distance between the two block centres. */
   public double distance() {
      double dx = this.bx - this.ax;
      double dy = this.by - this.ay;
      double dz = this.bz - this.az;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Distance ignoring height. */
   public double horizontal() {
      double dx = this.bx - this.ax;
      double dz = this.bz - this.az;
      return Math.sqrt(dx * dx + dz * dz);
   }

   /** Blocks walked along the grid (no diagonals). */
   public int gridSteps() {
      return Math.abs(this.bx - this.ax) + Math.abs(this.by - this.ay) + Math.abs(this.bz - this.az);
   }

   /** Blocks in the box spanned by the two blocks, both included. */
   public long volume() {
      return (long)this.sizeX() * this.sizeY() * this.sizeZ();
   }

   /** Blocks in the box's floor (width times length). */
   public long floorArea() {
      return (long)this.sizeX() * this.sizeZ();
   }

   /** "12.4 m". */
   public String distanceLabel() {
      if (this.distanceLabel == null) {
         this.distanceLabel = metres(this.distance());
      }

      return this.distanceLabel;
   }

   /** "5 x 3 x 9 = 135 blocks", or "1 block" for a single block. */
   public String sizeLabel() {
      if (this.sizeLabel == null) {
         long volume = this.volume();
         this.sizeLabel = this.sizeX() + " x " + this.sizeY() + " x " + this.sizeZ() + " = " + volume + (volume == 1L ? " block" : " blocks");
      }

      return this.sizeLabel;
   }

   /** The label drawn in the world: "9.2 m   5 x 3 x 9 = 135 blocks", or just "1 block" for a single block. */
   public String label() {
      if (this.label == null) {
         this.label = this.volume() == 1L ? "1 block" : this.distanceLabel() + "   " + this.sizeLabel();
      }

      return this.label;
   }

   /** One line for chat or the clipboard with everything measured. */
   public String summary() {
      return "From " + this.ax + " " + this.ay + " " + this.az + " to " + this.bx + " " + this.by + " " + this.bz
         + ": " + this.distanceLabel() + " (horizontal " + metres(this.horizontal()) + ", " + this.gridSteps() + " blocks along the grid), box "
         + this.sizeLabel() + ", floor " + this.floorArea();
   }

   /** A distance with one decimal, independent of the system locale. */
   public static String metres(double value) {
      return String.format(Locale.ROOT, "%.1f m", value);
   }
}
