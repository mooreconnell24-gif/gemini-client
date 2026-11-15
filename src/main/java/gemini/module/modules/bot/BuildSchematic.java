package gemini.module.modules.bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The patterns Auto Build Bot can build, as plain data: a list of blocks relative to an origin, each with the
 * material it needs, in the order they should be placed. Nothing here touches the game, so the layouts and their
 * ordering are unit tested.
 *
 * <p>Coordinates are in the builder's frame: {@code right} along the player's right, {@code up} up, {@code forward}
 * away from the player. Every list is ordered so that each block is placed after something it can rest against
 * (the ground row first, then upward and outward from blocks already placed); {@link Material#AIR} entries are
 * spots that must be cleared before anything else.
 */
public final class BuildSchematic {
   private BuildSchematic() {
   }

   public enum Material {
      OBSIDIAN,
      SOUL_SAND,
      WITHER_SKULL,
      /** Any block the module's material choice allows. */
      FILL,
      /** Must be empty. */
      AIR
   }

   public record Placement(int right, int up, int forward, Material material) {
   }

   /**
    * A nether portal frame, 4 wide and 5 tall, standing in the right/up plane with its bottom row at {@code up == 0}
    * and its left column at {@code right == 0}. The 2x3 inside is listed first as AIR. Without corners the frame is
    * the minimal ten obsidian; the sides then hang in the air and need air placement.
    */
   public static List<Placement> netherPortal(boolean corners) {
      List<Placement> list = new ArrayList<>();
      for (int up = 1; up <= 3; up++) {
         for (int right = 1; right <= 2; right++) {
            list.add(new Placement(right, up, 0, Material.AIR));
         }
      }

      if (corners) {
         list.add(new Placement(0, 0, 0, Material.OBSIDIAN));
      }

      list.add(new Placement(1, 0, 0, Material.OBSIDIAN));
      list.add(new Placement(2, 0, 0, Material.OBSIDIAN));
      if (corners) {
         list.add(new Placement(3, 0, 0, Material.OBSIDIAN));
      }

      for (int up = 1; up <= 3; up++) {
         list.add(new Placement(0, up, 0, Material.OBSIDIAN));
         list.add(new Placement(3, up, 0, Material.OBSIDIAN));
      }

      if (corners) {
         list.add(new Placement(0, 4, 0, Material.OBSIDIAN));
      }

      list.add(new Placement(1, 4, 0, Material.OBSIDIAN));
      list.add(new Placement(2, 4, 0, Material.OBSIDIAN));
      if (corners) {
         list.add(new Placement(3, 4, 0, Material.OBSIDIAN));
      }

      return list;
   }

   /** Where the portal is lit from: the top face of the left inner bottom block. */
   public static Placement portalIgnitionBase() {
      return new Placement(1, 0, 0, Material.OBSIDIAN);
   }

   /**
    * The wither: a soul sand T (stem at the origin, arms one block up) and three wither skeleton skulls on top,
    * the middle skull last since the last skull is what spawns it. The two spots beside the stem must be empty or
    * the pattern does not match.
    */
   public static List<Placement> wither() {
      List<Placement> list = new ArrayList<>();
      list.add(new Placement(-1, 0, 0, Material.AIR));
      list.add(new Placement(1, 0, 0, Material.AIR));
      list.add(new Placement(0, 0, 0, Material.SOUL_SAND));
      list.add(new Placement(0, 1, 0, Material.SOUL_SAND));
      list.add(new Placement(-1, 1, 0, Material.SOUL_SAND));
      list.add(new Placement(1, 1, 0, Material.SOUL_SAND));
      list.add(new Placement(-1, 2, 0, Material.WITHER_SKULL));
      list.add(new Placement(1, 2, 0, Material.WITHER_SKULL));
      list.add(new Placement(0, 2, 0, Material.WITHER_SKULL));
      return list;
   }

   /** A wall {@code length} long along the right axis and {@code height} tall, laid row by row from the ground. */
   public static List<Placement> wall(int length, int height) {
      List<Placement> list = new ArrayList<>();
      for (int up = 0; up < Math.max(1, height); up++) {
         for (int right = 0; right < Math.max(1, length); right++) {
            list.add(new Placement(right, up, 0, Material.FILL));
         }
      }

      return list;
   }

   /**
    * A square floor {@code size} wide centred on the origin, at {@code up == 0}, ordered outward from the centre so
    * each block after the first touches one placed before it.
    */
   public static List<Placement> platform(int size) {
      int n = Math.max(1, size);
      int low = -(n - 1) / 2;
      int high = low + n - 1;
      List<Placement> list = new ArrayList<>();
      for (int right = low; right <= high; right++) {
         for (int forward = low; forward <= high; forward++) {
            list.add(new Placement(right, 0, forward, Material.FILL));
         }
      }

      // Rings outward; inside a ring, the blocks touching the previous ring (straight out from it) come first.
      list.sort((a, b) -> {
         int ringA = Math.max(Math.abs(a.right()), Math.abs(a.forward()));
         int ringB = Math.max(Math.abs(b.right()), Math.abs(b.forward()));
         if (ringA != ringB) {
            return Integer.compare(ringA, ringB);
         }

         return Integer.compare(Math.abs(a.right()) + Math.abs(a.forward()), Math.abs(b.right()) + Math.abs(b.forward()));
      });
      return list;
   }

   /** A column {@code height} tall straight up from the origin. */
   public static List<Placement> pillar(int height) {
      List<Placement> list = new ArrayList<>();
      for (int up = 0; up < Math.max(1, height); up++) {
         list.add(new Placement(0, up, 0, Material.FILL));
      }

      return list;
   }

   /** A one-wide bridge {@code length} long running forward from the origin. */
   public static List<Placement> bridge(int length) {
      List<Placement> list = new ArrayList<>();
      for (int forward = 0; forward < Math.max(1, length); forward++) {
         list.add(new Placement(0, 0, forward, Material.FILL));
      }

      return list;
   }

   /** How many of each material a schematic needs (AIR excluded). */
   public static Map<Material, Integer> materials(List<Placement> schematic) {
      Map<Material, Integer> counts = new EnumMap<>(Material.class);
      for (Placement placement : schematic) {
         if (placement.material() != Material.AIR) {
            counts.merge(placement.material(), 1, Integer::sum);
         }
      }

      return Collections.unmodifiableMap(counts);
   }

   /**
    * True when every solid block can be placed against something already there when its turn comes: the first
    * block always can (it goes against whatever the origin touches), blocks on the bottom layer can when
    * {@code groundBelow} (they rest on the ground), and every other block must touch a solid block listed earlier.
    * The generators keep this true wherever a layout allows it; the tests check it.
    */
   public static boolean supportedInOrder(List<Placement> schematic, boolean groundBelow) {
      int minUp = Integer.MAX_VALUE;
      for (Placement placement : schematic) {
         if (placement.material() != Material.AIR) {
            minUp = Math.min(minUp, placement.up());
         }
      }

      List<Placement> placed = new ArrayList<>();
      for (Placement placement : schematic) {
         if (placement.material() == Material.AIR) {
            continue;
         }

         boolean supported = placed.isEmpty() || groundBelow && placement.up() == minUp;
         for (int i = 0; i < placed.size() && !supported; i++) {
            Placement before = placed.get(i);
            int distance = Math.abs(before.right() - placement.right()) + Math.abs(before.up() - placement.up()) + Math.abs(before.forward() - placement.forward());
            supported = distance == 1;
         }

         if (!supported) {
            return false;
         }

         placed.add(placement);
      }

      return true;
   }
}
