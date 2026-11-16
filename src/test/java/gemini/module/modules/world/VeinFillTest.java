package gemini.module.modules.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VeinFillTest {
   private static Set<Long> asSet(LongArrayList list) {
      Set<Long> set = new HashSet<>();
      for (int i = 0; i < list.size(); i++) {
         assertTrue(set.add(list.getLong(i)), "duplicate entry");
      }

      return set;
   }

   @Test
   void packingRoundTripsIncludingNegatives() {
      int[][] samples = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {123, -5, 456}};
      for (int[] p : samples) {
         long packed = VeinFill.pack(p[0], p[1], p[2]);
         assertEquals(p[0], VeinFill.unpackX(packed));
         assertEquals(p[1], VeinFill.unpackY(packed));
         assertEquals(p[2], VeinFill.unpackZ(packed));
      }
   }

   @Test
   void packingMatchesBlockPosLayout() {
      // BlockPos.asLong: x in the top 26 bits, z in the next 26, y in the low 12.
      assertEquals(1L << 38, VeinFill.pack(1, 0, 0));
      assertEquals(1L << 12, VeinFill.pack(0, 0, 1));
      assertEquals(1L, VeinFill.pack(0, 1, 0));
   }

   @Test
   void originIsNotIncludedAndLineIsFollowed() {
      // A straight line of ore along +x from the origin, 10 long.
      LongArrayList vein = VeinFill.collect(0, 0, 0, 100, false, (x, y, z) -> y == 0 && z == 0 && x >= 0 && x <= 10);
      Set<Long> set = asSet(vein);
      assertEquals(10, set.size());
      assertFalse(set.contains(VeinFill.pack(0, 0, 0)));
      assertTrue(set.contains(VeinFill.pack(10, 0, 0)));
   }

   @Test
   void limitCapsTheVein() {
      // Everything is ore: the fill must stop at the limit, not run away.
      assertEquals(32, VeinFill.collect(5, 5, 5, 32, true, (x, y, z) -> true).size());
      assertEquals(7, VeinFill.collect(5, 5, 5, 7, false, (x, y, z) -> true).size());
      assertEquals(0, VeinFill.collect(5, 5, 5, 0, true, (x, y, z) -> true).size());
   }

   @Test
   void nearestLayerComesFirst() {
      LongArrayList vein = VeinFill.collect(0, 0, 0, 6, false, (x, y, z) -> true);
      // With face neighbours only, the first six are exactly the six faces of the origin.
      for (int i = 0; i < vein.size(); i++) {
         long p = vein.getLong(i);
         int steps = Math.abs(VeinFill.unpackX(p)) + Math.abs(VeinFill.unpackY(p)) + Math.abs(VeinFill.unpackZ(p));
         assertEquals(1, steps);
      }
   }

   @Test
   void diagonalsControlCornerConnections() {
      // Two ore blocks touching the origin and each other only at corners.
      VeinFill.Member corners = (x, y, z) -> x == y && y == z && (x == 1 || x == 2);
      assertEquals(0, VeinFill.collect(0, 0, 0, 10, false, corners).size());
      assertEquals(2, VeinFill.collect(0, 0, 0, 10, true, corners).size());
   }

   @Test
   void nonMembersBlockTheFill() {
      // Ore at x = 1 and x = 3 with a gap at x = 2: only x = 1 is connected.
      LongArrayList vein = VeinFill.collect(0, 0, 0, 10, true, (x, y, z) -> y == 0 && z == 0 && (x == 1 || x == 3));
      assertEquals(1, vein.size());
      assertEquals(VeinFill.pack(1, 0, 0), vein.getLong(0));
   }
}
