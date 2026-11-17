package gemini.util;

/**
 * Who to shoot or hit first. Pure maths over a few numbers per candidate, so the order can be unit tested and the
 * per-tick pick needs no list and no sort: each candidate gets a score, lower is better, and the best one wins.
 */
public final class TargetPriority {
   /** Priority modes, in the order the menus show them. */
   public static final String CLOSEST = "Closest";
   public static final String LOWEST_HEALTH = "Lowest Health";
   public static final String HIGHEST_HEALTH = "Highest Health";
   public static final String CROSSHAIR = "Closest To Crosshair";
   public static final String MOST_ARMOR = "Most Armor";
   public static final String LEAST_ARMOR = "Least Armor";
   public static final String MOST_DANGEROUS = "Most Dangerous";
   public static final String[] MODES = {CLOSEST, LOWEST_HEALTH, HIGHEST_HEALTH, CROSSHAIR, MOST_ARMOR, LEAST_ARMOR, MOST_DANGEROUS};
   /** Which kind of target is tried first, before the priority mode orders the rest. */
   public static final String PREFER_ANY = "Any";
   public static final String PREFER_PLAYERS = "Players First";
   public static final String PREFER_MOBS = "Mobs First";
   public static final String[] PREFER = {PREFER_ANY, PREFER_PLAYERS, PREFER_MOBS};
   /** Added to the score of a candidate of the kind that is not preferred: larger than any score a mode produces. */
   private static final double KIND_PENALTY = 1.0E9;

   private TargetPriority() {
   }

   /**
    * The score of one candidate; lower is better.
    *
    * @param distanceSq squared distance from the player's eyes
    * @param health current health plus absorption
    * @param armor armour points (0-30)
    * @param angle angle in degrees between where the player looks and the candidate (0-180)
    * @param player whether the candidate is a player
    * @param threat how dangerous the candidate is right now, 0 or more (see {@link #threat})
    */
   public static double score(String mode, String prefer, double distanceSq, double health, double armor, double angle, boolean player, double threat) {
      // The distance term breaks ties in every mode, small enough never to outweigh the mode's own measure.
      double tie = Math.sqrt(Math.max(0.0, distanceSq)) * 1.0E-3;
      double base = switch (mode) {
         case LOWEST_HEALTH -> health + tie;
         case HIGHEST_HEALTH -> -health + tie;
         case CROSSHAIR -> angle + tie;
         case MOST_ARMOR -> -armor + tie;
         case LEAST_ARMOR -> armor + tie;
         case MOST_DANGEROUS -> -threat + tie;
         default -> distanceSq;
      };
      boolean preferred = switch (prefer) {
         case PREFER_PLAYERS -> player;
         case PREFER_MOBS -> !player;
         default -> true;
      };
      return preferred ? base : base + KIND_PENALTY;
   }

   /**
    * How dangerous a candidate is: something already attacking you counts most, then anything holding a weapon or
    * hostile by nature, and closer is worse. Pure so the weighting is tested.
    *
    * @param targetingYou the candidate has the player as its attack target (mobs) or is looking straight at them (players)
    * @param armed holds a weapon or is a hostile mob
    * @param attackDamage the damage its held item or natural attack does, 0 if unknown
    */
   public static double threat(boolean targetingYou, boolean armed, double attackDamage, double distance) {
      double closeness = 1.0 / Math.max(1.0, distance);
      return (targetingYou ? 4.0 : 0.0) + (armed ? 2.0 : 0.0) + Math.max(0.0, attackDamage) * 0.25 + closeness * 3.0;
   }

   /** Angle in degrees between a look direction and the direction to a point; both directions need not be unit length. */
   public static double angleTo(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
      double lookLen = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
      double toLen = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
      if (lookLen < 1.0E-9 || toLen < 1.0E-9) {
         return 0.0;
      }

      double cos = (lookX * toX + lookY * toY + lookZ * toZ) / (lookLen * toLen);
      return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
   }

   /** Colour from red (empty) through yellow to green (full) for a health fraction, opaque ARGB. */
   public static int healthColor(double fraction) {
      double f = Math.max(0.0, Math.min(1.0, fraction));
      int r = f < 0.5 ? 255 : (int)Math.round(255.0 * (1.0 - f) * 2.0);
      int g = f > 0.5 ? 220 : (int)Math.round(220.0 * f * 2.0);
      return 0xFF000000 | r << 16 | g << 8 | 0x40;
   }
}
