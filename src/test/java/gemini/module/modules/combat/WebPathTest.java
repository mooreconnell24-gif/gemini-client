package gemini.module.modules.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebPathTest {
   /** Flat ground at y = 0 everywhere. */
   private static final WebPath.Walkable FLAT = (x, y, z) -> y == 0;

   @Test
   void standingStillGivesOnlyTheCurrentBlock() {
      List<int[]> path = WebPath.predict(0.5, 0.0, 0.5, 0.0, 0.0, 10, 1.0, FLAT);
      assertEquals(1, path.size());
      assertArrayEquals(new int[]{0, 0, 0}, path.get(0));
   }

   @Test
   void walkingEastVisitsEachBlockOnce() {
      List<int[]> path = WebPath.predict(0.5, 0.0, 0.5, 0.3, 0.0, 10, 1.0, FLAT);
      // 0.5 + 10 * 0.3 = 3.5, so blocks 0..3 in order
      assertEquals(4, path.size());
      for (int i = 0; i < path.size(); i++) {
         assertArrayEquals(new int[]{i, 0, 0}, path.get(i));
      }
   }

   @Test
   void dragShortensThePath() {
      int free = WebPath.predict(0.5, 0.0, 0.5, 0.4, 0.0, 20, 1.0, FLAT).size();
      int dragged = WebPath.predict(0.5, 0.0, 0.5, 0.4, 0.0, 20, WebPath.GROUND_DRAG, FLAT).size();
      assertTrue(dragged < free, "drag should stop the target sooner");
   }

   @Test
   void wallEndsThePath() {
      WebPath.Walkable wall = (x, y, z) -> y == 0 && x < 2;
      List<int[]> path = WebPath.predict(0.5, 0.0, 0.5, 0.5, 0.0, 10, 1.0, wall);
      assertEquals(2, path.size());
      assertArrayEquals(new int[]{1, 0, 0}, path.get(path.size() - 1));
   }

   @Test
   void followsStepsUpAndSmallDrops() {
      // x = 1 is one block higher; x = 2 drops two blocks below the start.
      WebPath.Walkable terrain = (x, y, z) -> x <= 0 ? y == 0 : x == 1 ? y == 1 : y == -1;
      List<int[]> path = WebPath.predict(0.5, 0.0, 0.5, 0.5, 0.0, 4, 1.0, terrain);
      assertArrayEquals(new int[]{1, 1, 0}, path.get(1));
      assertArrayEquals(new int[]{2, -1, 0}, path.get(2));
   }

   @Test
   void deepCliffEndsThePath() {
      WebPath.Walkable cliff = (x, y, z) -> x <= 0 ? y == 0 : y == -5;
      assertEquals(1, WebPath.predict(0.5, 0.0, 0.5, 0.5, 0.0, 6, 1.0, cliff).size());
   }

   @Test
   void negativeCoordinatesRoundDown() {
      List<int[]> path = WebPath.predict(-0.5, 0.0, -0.5, 0.0, -0.6, 1, 1.0, FLAT);
      assertArrayEquals(new int[]{-1, 0, -1}, path.get(0));
      assertArrayEquals(new int[]{-1, 0, -2}, path.get(1));
   }
}
