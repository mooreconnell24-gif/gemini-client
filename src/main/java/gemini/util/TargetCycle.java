package gemini.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Round-robin over targets for "Switch" style auras: every {@code interval} ticks the current target is marked as
 * visited and the next pick skips visited ones; once every candidate has been visited the round starts again.
 * Works with any picker (the priority order decides who comes next), keeps only entity ids and allocates nothing
 * per tick once warm.
 */
public final class TargetCycle {
   private final IntOpenHashSet visited = new IntOpenHashSet();
   private int timer;

   /** Counts one tick; true (and restarts the count) once {@code interval} ticks have passed. */
   public boolean tick(int interval) {
      if (++this.timer >= interval) {
         this.timer = 0;
         return true;
      }

      return false;
   }

   /** Marks the entity with this id as already had its turn in this round. */
   public void visit(int id) {
      this.visited.add(id);
   }

   public boolean visited(int id) {
      return this.visited.contains(id);
   }

   /** Whether anything has been visited this round (a pick that found nobody should then start a new round). */
   public boolean midRound() {
      return !this.visited.isEmpty();
   }

   /** Starts a new round: nothing visited, timer back to zero. */
   public void reset() {
      this.visited.clear();
      this.timer = 0;
   }

   /** Starts a new round but keeps the timer running (the current target keeps the rest of its turn). */
   public void newRound() {
      this.visited.clear();
   }
}
