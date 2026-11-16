package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatTargetingTest {
   @Test
   void slotKeepsTheLatestReportUntilItGoesStale() {
      CombatTarget.Slot<String, String> slot = new CombatTarget.Slot<>(300L);
      assertNull(slot.get(0L));
      slot.report("aura", "zombie", 1000L);
      assertEquals("zombie", slot.get(1300L));
      assertNull(slot.get(1301L), "a report that is not refreshed expires");
      slot.report("trigger", "skeleton", 1400L);
      assertEquals("skeleton", slot.get(1400L), "the most recent report wins");
      assertEquals("trigger", slot.owner());
   }

   @Test
   void onlyTheReportingOwnerCanReleaseTheSlot() {
      CombatTarget.Slot<String, String> slot = new CombatTarget.Slot<>(300L);
      slot.report("aura", "zombie", 0L);
      slot.release("trigger");
      assertEquals("zombie", slot.get(0L));
      slot.release("aura");
      assertNull(slot.get(0L));
      assertNull(slot.owner());
   }

   @Test
   void cycleTimerFiresEveryInterval() {
      TargetCycle cycle = new TargetCycle();
      int fired = 0;
      for (int i = 0; i < 18; i++) {
         if (cycle.tick(6)) {
            fired++;
         }
      }

      assertEquals(3, fired);
   }

   @Test
   void cycleVisitsEveryCandidateOnceBeforeStartingOver() {
      // Candidates in priority order; each turn picks the best one not yet visited, like KillAura's Switch mode.
      List<Integer> candidates = List.of(7, 3, 9);
      TargetCycle cycle = new TargetCycle();
      List<Integer> order = new ArrayList<>();
      Integer current = null;
      for (int turn = 0; turn < 6; turn++) {
         if (current != null) {
            cycle.visit(current);
         }

         current = null;
         for (int id : candidates) {
            if (!cycle.visited(id)) {
               current = id;
               break;
            }
         }

         if (current == null && cycle.midRound()) {
            cycle.newRound();
            current = candidates.get(0);
         }

         order.add(current);
      }

      assertEquals(List.of(7, 3, 9, 7, 3, 9), order);
   }

   @Test
   void resetClearsVisitsAndTimer() {
      TargetCycle cycle = new TargetCycle();
      cycle.tick(6);
      cycle.visit(4);
      assertTrue(cycle.midRound());
      cycle.reset();
      assertFalse(cycle.midRound());
      assertFalse(cycle.visited(4));
      for (int i = 0; i < 5; i++) {
         assertFalse(cycle.tick(6), "timer restarted from zero");
      }

      assertTrue(cycle.tick(6));
   }
}
