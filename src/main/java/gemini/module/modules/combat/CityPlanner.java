package gemini.module.modules.combat;

import java.util.Comparator;
import java.util.List;

/**
 * Which surround block Auto City mines, kept free of game classes so the ranking can be unit tested.
 *
 * <p>In order: a block that opens a crystal spot beats one that does not; a quicker break beats a slower one
 * (an ender chest before obsidian); a closer block beats a farther one. Blocks that cannot be broken at all
 * (bedrock, out of reach) never get here.
 */
public final class CityPlanner {
   /** Break times within this many ticks of each other count as equal, so distance can decide between them. */
   static final int BREAK_TIME_SLACK = 5;

   private CityPlanner() {
   }

   /**
    * One minable surround block.
    *
    * @param id the caller's handle for the block (an index into its own list)
    * @param crystalSpot whether a crystal can go next to the target once this block is gone
    * @param breakTicks how long breaking it takes with the best tool
    * @param distanceSq squared distance from the player's eyes
    */
   public record Candidate(int id, boolean crystalSpot, int breakTicks, double distanceSq) {
   }

   static final Comparator<Candidate> ORDER = Comparator.<Candidate>comparingInt(c -> c.crystalSpot() ? 0 : 1)
      .thenComparingInt(c -> c.breakTicks() / BREAK_TIME_SLACK)
      .thenComparingDouble(Candidate::distanceSq);

   /** The best candidate, or null for an empty list. */
   public static Candidate best(List<Candidate> candidates) {
      Candidate best = null;
      for (Candidate candidate : candidates) {
         if (best == null || ORDER.compare(candidate, best) < 0) {
            best = candidate;
         }
      }

      return best;
   }
}
