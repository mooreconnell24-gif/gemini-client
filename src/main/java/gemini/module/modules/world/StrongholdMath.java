package gemini.module.modules.world;

/**
 * Plain maths for Stronghold Finder: the heading of a thrown eye and where two throws' lines cross. No game
 * classes, so it is unit tested.
 */
public final class StrongholdMath {
   /** Throws whose lines differ by less than this (in degrees) are too close to parallel to cross reliably. */
   public static final double MIN_ANGLE_DEGREES = 3.0;

   private StrongholdMath() {
   }

   /** Minecraft yaw in degrees (0 = +Z/south, 90 = -X/west) of the horizontal direction (dx, dz), in [-180, 180]. */
   public static double yaw(double dx, double dz) {
      return Math.toDegrees(Math.atan2(-dx, dz));
   }

   /**
    * Where the line from (x1, z1) along (dx1, dz1) meets the line from (x2, z2) along (dx2, dz2), ahead of both
    * throw points. Returns {x, z}, or null when the lines are nearly parallel (less than
    * {@link #MIN_ANGLE_DEGREES} apart), a direction is zero, or the crossing lies behind either thrower.
    */
   public static double[] intersect(double x1, double z1, double dx1, double dz1, double x2, double z2, double dx2, double dz2) {
      double length1 = Math.hypot(dx1, dz1);
      double length2 = Math.hypot(dx2, dz2);
      if (length1 < 1.0E-9 || length2 < 1.0E-9) {
         return null;
      }

      double ux1 = dx1 / length1;
      double uz1 = dz1 / length1;
      double ux2 = dx2 / length2;
      double uz2 = dz2 / length2;
      // The cross product of the unit directions is the sine of the angle between them.
      double cross = ux1 * uz2 - uz1 * ux2;
      if (Math.abs(cross) < Math.sin(Math.toRadians(MIN_ANGLE_DEGREES))) {
         return null;
      }

      double ox = x2 - x1;
      double oz = z2 - z1;
      double t = (ox * uz2 - oz * ux2) / cross;
      double s = (ox * uz1 - oz * ux1) / cross;
      if (t < 0.0 || s < 0.0) {
         return null;
      }

      return new double[]{x1 + ux1 * t, z1 + uz1 * t};
   }
}
