package gemini.module.modules.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BiomeEdgesTest {
   @Test
   void oneBiomeHasNoBorders() {
      int[] ids = new int[16];
      assertEquals(0, BiomeEdges.compute(ids, 4, 4, new int[64]));
   }

   @Test
   void straightBorderIsOneMergedSegment() {
      // Columns 0-1 are biome 0, columns 2-3 biome 1: a single line at x = 2 from z = 0 to z = 3.
      int[] ids = {
         0, 0, 1, 1,
         0, 0, 1, 1,
         0, 0, 1, 1
      };
      int[] out = new int[64];
      int n = BiomeEdges.compute(ids, 4, 3, out);
      assertEquals(1, n);
      assertArrayEquals(new int[]{2, 0, 2, 3}, Arrays.copyOf(out, 4));
   }

   @Test
   void cornerPatchMakesTwoSegments() {
      // A 1x1 patch of biome 5 in the top-left corner of a 3x3 grid.
      int[] ids = {
         5, 0, 0,
         0, 0, 0,
         0, 0, 0
      };
      int[] out = new int[64];
      int n = BiomeEdges.compute(ids, 3, 3, out);
      assertEquals(2, n);
      assertArrayEquals(new int[]{1, 0, 1, 1, 0, 1, 1, 1}, Arrays.copyOf(out, 8));
   }

   @Test
   void unknownCellsNeverMakeBorders() {
      int[] ids = {
         0, -1, 1,
         0, -1, 1
      };
      assertEquals(0, BiomeEdges.compute(ids, 3, 2, new int[64]));
   }

   @Test
   void interruptedBorderSplitsIntoTwo() {
      int[] ids = {
         0, 1,
         0, 0,
         0, 1
      };
      int[] out = new int[64];
      int n = BiomeEdges.compute(ids, 2, 3, out);
      // x = 1 between rows 0 and 1, again between rows 2 and 3, plus two horizontal pieces under and over row 1.
      assertEquals(4, n);
      assertArrayEquals(new int[]{1, 0, 1, 1, 1, 2, 1, 3}, Arrays.copyOf(out, 8));
   }

   @Test
   void fullOutputKeepsWhatFits() {
      int[] ids = {
         0, 1, 0, 1,
         1, 0, 1, 0
      };
      int[] out = new int[8];
      assertEquals(2, BiomeEdges.compute(ids, 4, 2, out));
   }
}
