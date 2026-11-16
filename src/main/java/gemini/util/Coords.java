package gemini.util;

import java.util.Locale;

/**
 * Pure coordinate helpers shared by Coords Copier and Spawn Info: text formats, dimension names, the
 * Overworld/Nether 8:1 conversion, distances and an eight-way compass heading. Dimensions are passed as their id
 * strings ("minecraft:the_nether") so all of it runs without a world.
 */
public final class Coords {
   public static final String OVERWORLD = "minecraft:overworld";
   public static final String NETHER = "minecraft:the_nether";
   public static final String END = "minecraft:the_end";
   private static final String[] HEADINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

   private Coords() {
   }

   /** "Overworld", "Nether", "End", or the path of a modded dimension id. */
   public static String dimensionName(String id) {
      if (id == null) {
         return "?";
      }

      return switch (id) {
         case OVERWORLD -> "Overworld";
         case NETHER -> "Nether";
         case END -> "End";
         default -> {
            int colon = id.indexOf(':');
            yield colon >= 0 ? id.substring(colon + 1) : id;
         }
      };
   }

   /**
    * How many blocks in {@code to} one block in {@code from} is worth horizontally: 1/8 going into the Nether, 8
    * coming out of it, 1 inside one dimension. NaN when the two are not linked by portals that way (the End).
    */
   public static double scale(String from, String to) {
      if (from == null || to == null) {
         return Double.NaN;
      } else if (from.equals(to)) {
         return 1.0;
      } else if (OVERWORLD.equals(from) && NETHER.equals(to)) {
         return 0.125;
      } else {
         return NETHER.equals(from) && OVERWORLD.equals(to) ? 8.0 : Double.NaN;
      }
   }

   /**
    * Horizontal distance from a spot in {@code fromDim} to a spot in {@code toDim}, measured in {@code fromDim}
    * after converting the target across the Overworld/Nether link. NaN when the dimensions are not linked.
    */
   public static double distance(String fromDim, double x, double z, String toDim, double tx, double tz) {
      double factor = scale(toDim, fromDim);
      if (Double.isNaN(factor)) {
         return Double.NaN;
      }

      return Math.hypot(tx * factor - x, tz * factor - z);
   }

   /** The compass direction of a horizontal offset (+X east, +Z south), one of eight; "-" for no offset. */
   public static String heading(double dx, double dz) {
      if (dx == 0.0 && dz == 0.0) {
         return "-";
      }

      // atan2 measured from +Z (south) turning toward -X (west), the way Minecraft yaw runs.
      double degrees = Math.toDegrees(Math.atan2(-dx, dz));
      int index = (int)Math.floorMod(Math.round(degrees / 45.0), 8L);
      return HEADINGS[index];
   }

   /** One of the copy formats: "Plain" (x y z), "Comma" (x, y, z), "Teleport" (/tp x y z) or "Execute" (/execute in dim run tp). */
   public static String format(String mode, String dimension, double x, double y, double z, boolean exact) {
      String xs = number(x, exact);
      String ys = number(y, exact);
      String zs = number(z, exact);
      return switch (mode) {
         case "Comma" -> xs + ", " + ys + ", " + zs;
         case "Teleport" -> "/tp @s " + xs + " " + ys + " " + zs;
         case "Execute" -> "/execute in " + (dimension == null ? OVERWORLD : dimension) + " run tp @s " + xs + " " + ys + " " + zs;
         default -> xs + " " + ys + " " + zs;
      };
   }

   /** Block coordinates (floored) or the exact position with two decimals, always with a dot. */
   public static String number(double value, boolean exact) {
      return exact ? String.format(Locale.ROOT, "%.2f", value) : Long.toString((long)Math.floor(value));
   }
}
