package gemini.module.modules.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegearPlanTest {
   private static Map<String, Integer> map(Object... pairs) {
      Map<String, Integer> out = new LinkedHashMap<>();
      for (int i = 0; i < pairs.length; i += 2) {
         out.put((String)pairs[i], (Integer)pairs[i + 1]);
      }

      return out;
   }

   @Test
   void deficitsKeepOnlyWhatIsShortInWantedOrder() {
      Map<String, Integer> missing = RegearPlan.deficits(map("totems", 6, "crystals", 128, "pearls", 16), map("totems", 2, "pearls", 20));
      assertEquals(map("totems", 4, "crystals", 128), missing);
      assertEquals(List.of("totems", "crystals"), List.copyOf(missing.keySet()));
   }

   @Test
   void nothingMissingMeansNoPick() {
      assertEquals(-1, RegearPlan.nextPick(Arrays.asList("totems", null), new int[]{1, 0}, map()));
   }

   @Test
   void unwantedAndEmptySlotsAreNeverPicked() {
      List<String> keys = Arrays.asList(null, "dirt", "totems");
      int[] counts = {64, 64, 0};
      assertEquals(-1, RegearPlan.nextPick(keys, counts, map("totems", 3)));
   }

   @Test
   void prefersTheLargestStackThatFitsTheGap() {
      List<String> keys = List.of("crystals", "crystals", "crystals");
      int[] counts = {64, 16, 32};
      assertEquals(2, RegearPlan.nextPick(keys, counts, map("crystals", 40)));
   }

   @Test
   void overshootsAsLittleAsPossibleWhenNothingFits() {
      List<String> keys = List.of("totems", "totems");
      int[] counts = {64, 3};
      assertEquals(1, RegearPlan.nextPick(keys, counts, map("totems", 2)));
   }
}
