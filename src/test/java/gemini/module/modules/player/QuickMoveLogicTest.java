package gemini.module.modules.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Quick Move's drag path and Craft Repeat's decision loop. */
class QuickMoveLogicTest {
   /** A 9-wide row of slots at the vanilla 18px pitch, starting at x = 8, y = 84. */
   private static final int[] ROW_X = {8, 26, 44, 62, 80, 98, 116, 134, 152};
   private static final int[] ROW_Y = {84, 84, 84, 84, 84, 84, 84, 84, 84};

   @Test
   void slotUnderPointIncludesTheHoverMargin() {
      assertEquals(0, DragPath.slotAt(8.0, 84.0, ROW_X, ROW_Y, 9));
      assertEquals(0, DragPath.slotAt(7.0, 83.0, ROW_X, ROW_Y, 9));
      assertEquals(1, DragPath.slotAt(25.0, 90.0, ROW_X, ROW_Y, 9), "the gap between two slots belongs to one of them");
      assertEquals(-1, DragPath.slotAt(8.0, 50.0, ROW_X, ROW_Y, 9));
   }

   @Test
   void fastDragAcrossTheRowHitsEverySlotInOrder() {
      int[] out = new int[16];
      int n = DragPath.crossed(10.0, 90.0, 165.0, 90.0, ROW_X, ROW_Y, 9, out);
      assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, Arrays.copyOf(out, n));
   }

   @Test
   void dragBackwardsAndStandingStill() {
      int[] out = new int[16];
      int n = DragPath.crossed(100.0, 90.0, 50.0, 90.0, ROW_X, ROW_Y, 9, out);
      assertArrayEquals(new int[]{5, 4, 3, 2}, Arrays.copyOf(out, n));
      assertEquals(1, DragPath.crossed(30.0, 90.0, 30.0, 90.0, ROW_X, ROW_Y, 9, out));
      assertEquals(1, out[0]);
   }

   @Test
   void outputIsCappedByItsLength() {
      int[] out = new int[3];
      assertEquals(3, DragPath.crossed(10.0, 90.0, 165.0, 90.0, ROW_X, ROW_Y, 9, out));
      assertArrayEquals(new int[]{0, 1, 2}, out);
   }

   @Test
   void craftCycleRefillsAfterEachTakeUntilIngredientsRunOut() {
      CraftCycle cycle = new CraftCycle();
      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 0), "nothing happens before a recipe is placed");
      cycle.arm();
      // The hand placement shows up a tick later.
      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 0));
      assertEquals(CraftCycle.Action.WAIT, cycle.step(false, true, true, 0, 0));
      assertEquals(CraftCycle.Action.TAKE, cycle.step(false, true, true, 0, 0));
      // The result is gone: counted, then the recipe goes back in.
      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 0));
      assertEquals(1, cycle.crafts());
      assertEquals(CraftCycle.Action.PLACE, cycle.step(true, true, true, 0, 0));
      for (int i = 0; i < CraftCycle.PLACE_TIMEOUT; i++) {
         assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 0));
      }

      assertEquals(CraftCycle.Action.OUT, cycle.step(true, true, true, 0, 0));
      assertEquals(false, cycle.armed());
   }

   @Test
   void craftCycleWithoutAutoTakeWaitsForYou() {
      CraftCycle cycle = new CraftCycle();
      cycle.arm();
      assertEquals(CraftCycle.Action.WAIT, cycle.step(false, true, false, 0, 0));
      for (int i = 0; i < 5; i++) {
         assertEquals(CraftCycle.Action.WAIT, cycle.step(false, true, false, 0, 0));
      }

      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, false, 0, 0));
      assertEquals(CraftCycle.Action.PLACE, cycle.step(true, true, false, 0, 0));
   }

   @Test
   void craftCycleHonoursCursorDelayAndLimit() {
      CraftCycle cycle = new CraftCycle();
      cycle.arm();
      cycle.step(false, true, true, 1, 2);
      assertEquals(CraftCycle.Action.WAIT, cycle.step(false, false, true, 1, 2), "an item on the cursor blocks the click");
      assertEquals(CraftCycle.Action.TAKE, cycle.step(false, true, true, 1, 2));
      cycle.step(true, true, true, 1, 2);
      assertEquals(CraftCycle.Action.DONE, cycle.step(true, true, true, 1, 2));
   }

   @Test
   void craftCycleStopsWhenTakingDoesNothing() {
      CraftCycle cycle = new CraftCycle();
      cycle.arm();
      cycle.step(false, true, true, 0, 0);
      for (int i = 0; i < CraftCycle.MAX_TAKES; i++) {
         assertEquals(CraftCycle.Action.TAKE, cycle.step(false, true, true, 0, 0));
      }

      assertEquals(CraftCycle.Action.FULL, cycle.step(false, true, true, 0, 0));
   }

   @Test
   void craftCycleWaitsOutTheDelayBetweenItsOwnClicks() {
      CraftCycle cycle = new CraftCycle();
      cycle.arm();
      cycle.step(false, true, true, 0, 3);
      assertEquals(CraftCycle.Action.TAKE, cycle.step(false, true, true, 0, 3));
      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 3));
      assertEquals(CraftCycle.Action.WAIT, cycle.step(true, true, true, 0, 3));
      assertEquals(CraftCycle.Action.PLACE, cycle.step(true, true, true, 0, 3));
   }
}
