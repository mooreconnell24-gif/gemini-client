package gemini.module.modules.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The arithmetic behind Auto Regear, kept free of game classes so it can be unit tested: what is still missing
 * compared with the wanted layout, and which container stacks to take to close the gap.
 *
 * <p>A "key" is whatever groups stacks that count toward the same target (a category such as "totems", or one
 * item id). Stacks without a key are not wanted and are never taken.
 */
public final class RegearPlan {
   private RegearPlan() {
   }

   /** What is still missing per key: {@code wanted - have}, only where that is positive, in the order of {@code wanted}. */
   public static <K> Map<K, Integer> deficits(Map<K, Integer> wanted, Map<K, Integer> have) {
      Map<K, Integer> missing = new LinkedHashMap<>();
      for (Map.Entry<K, Integer> entry : wanted.entrySet()) {
         int target = entry.getValue() == null ? 0 : entry.getValue();
         int owned = have.getOrDefault(entry.getKey(), 0);
         if (target > owned) {
            missing.put(entry.getKey(), target - owned);
         }
      }

      return missing;
   }

   /**
    * The container slot to take next, or -1 when nothing in the container closes a gap. Whole stacks are taken, so
    * the stack that overshoots least is preferred: the largest one that still fits the gap, otherwise the smallest.
    *
    * @param slotKeys the key of the stack in each container slot, null for an empty or unwanted slot
    * @param slotCounts the stack size in each container slot
    */
   public static <K> int nextPick(List<K> slotKeys, int[] slotCounts, Map<K, Integer> deficits) {
      int best = -1;
      boolean bestFits = false;
      int bestCount = 0;
      for (int slot = 0; slot < slotKeys.size() && slot < slotCounts.length; slot++) {
         K key = slotKeys.get(slot);
         int count = slotCounts[slot];
         if (key == null || count <= 0) {
            continue;
         }

         int gap = deficits.getOrDefault(key, 0);
         if (gap <= 0) {
            continue;
         }

         boolean fits = count <= gap;
         boolean better;
         if (best < 0) {
            better = true;
         } else if (fits != bestFits) {
            better = fits;
         } else {
            better = fits ? count > bestCount : count < bestCount;
         }

         if (better) {
            best = slot;
            bestFits = fits;
            bestCount = count;
         }
      }

      return best;
   }
}
