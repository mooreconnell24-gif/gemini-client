package gemini.util.pathing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.PriorityQueue;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PathSearchTest {
   private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

   /** A flat grid at y = 0: walk in eight directions, diagonals cost more, walls block. */
   private static final class Grid implements PathSearch.MoveGenerator {
      final int size;
      final boolean[] wall;
      int expansions;

      Grid(int size) {
         this.size = size;
         this.wall = new boolean[size * size];
      }

      boolean open(int x, int z) {
         return x >= 0 && z >= 0 && x < this.size && z < this.size && !this.wall[z * this.size + x];
      }

      void wall(int x, int z) {
         this.wall[z * this.size + x] = true;
      }

      @Override
      public void expand(int x, int y, int z, PathSearch.Sink sink) {
         this.expansions++;
         for (int[] n : NEIGHBOURS) {
            int nx = x + n[0];
            int nz = z + n[1];
            if (this.open(nx, nz)) {
               sink.offer(nx, 0, nz, n[0] != 0 && n[1] != 0 ? PathSearch.DIAGONAL : 1.0);
            }
         }
      }

      /** Plain Dijkstra, as the reference for the cheapest cost. */
      double cheapest(int sx, int sz, int gx, int gz) {
         double[] best = new double[this.size * this.size];
         java.util.Arrays.fill(best, Double.MAX_VALUE);
         PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
         best[sz * this.size + sx] = 0.0;
         queue.add(new double[]{0.0, sx, sz});
         while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int x = (int)top[1];
            int z = (int)top[2];
            if (top[0] > best[z * this.size + x]) {
               continue;
            }

            if (x == gx && z == gz) {
               return top[0];
            }

            for (int[] n : NEIGHBOURS) {
               int nx = x + n[0];
               int nz = z + n[1];
               if (this.open(nx, nz)) {
                  double cost = top[0] + (n[0] != 0 && n[1] != 0 ? PathSearch.DIAGONAL : 1.0);
                  if (cost < best[nz * this.size + nx]) {
                     best[nz * this.size + nx] = cost;
                     queue.add(new double[]{cost, nx, nz});
                  }
               }
            }
         }

         return Double.MAX_VALUE;
      }
   }

   private static PathSearch exact() {
      return new PathSearch().tune(1.0, 0.65, 0.3);
   }

   private static double pathCost(PathSearch search) {
      return search.pathLength() == 0 ? 0.0 : search.stepCost(search.pathLength() - 1);
   }

   @Test
   void openGroundCostsTheOctileDistance() {
      Grid grid = new Grid(64);
      PathSearch search = exact();
      int steps = search.search(2, 0, 3, 40, 0, 20, 0, 100000, Long.MAX_VALUE, grid);
      assertTrue(search.reachedGoal());
      // 17 diagonal steps and 21 straight ones.
      assertEquals(38, steps);
      assertEquals(17 * PathSearch.DIAGONAL + 21, pathCost(search), 1e-9);
      assertEquals(40, search.stepX(steps - 1));
      assertEquals(20, search.stepZ(steps - 1));
   }

   @Test
   void everyStepIsOneMoveFromTheLast() {
      Grid grid = new Grid(48);
      for (int z = 0; z < 40; z++) {
         grid.wall(20, z);
      }

      PathSearch search = exact();
      int steps = search.search(5, 0, 5, 35, 0, 5, 0, 100000, Long.MAX_VALUE, grid);
      assertTrue(search.reachedGoal());
      int x = 5;
      int z = 5;
      for (int i = 0; i < steps; i++) {
         assertTrue(Math.abs(search.stepX(i) - x) <= 1 && Math.abs(search.stepZ(i) - z) <= 1, "step " + i + " jumps");
         assertTrue(grid.open(search.stepX(i), search.stepZ(i)), "step " + i + " is inside a wall");
         x = search.stepX(i);
         z = search.stepZ(i);
      }
   }

   @Test
   void matchesDijkstraOnRandomMazes() {
      Random random = new Random(42);
      PathSearch search = exact();
      for (int round = 0; round < 40; round++) {
         Grid grid = new Grid(40);
         for (int i = 0; i < 480; i++) {
            grid.wall(random.nextInt(40), random.nextInt(40));
         }

         grid.wall[0] = false;
         grid.wall[39 * 40 + 39] = false;
         double expected = grid.cheapest(0, 0, 39, 39);
         search.search(0, 0, 0, 39, 0, 39, 0, 100000, Long.MAX_VALUE, grid);
         if (expected == Double.MAX_VALUE) {
            assertFalse(search.reachedGoal(), "round " + round);
         } else {
            assertTrue(search.reachedGoal(), "round " + round);
            assertEquals(expected, pathCost(search), 1e-6, "round " + round);
         }
      }
   }

   @Test
   void weightedSearchStaysCloseToOptimalAndExpandsLess() {
      Random random = new Random(7);
      Grid grid = new Grid(96);
      for (int i = 0; i < 1800; i++) {
         grid.wall(1 + random.nextInt(94), 1 + random.nextInt(94));
      }

      grid.wall[0] = false;
      grid.wall[95 * 96 + 95] = false;
      double optimal = grid.cheapest(0, 0, 95, 95);
      PathSearch exact = exact();
      exact.search(0, 0, 0, 95, 0, 95, 0, 1000000, Long.MAX_VALUE, grid);
      PathSearch weighted = new PathSearch().tune(1.1, 0.65, 0.3);
      weighted.search(0, 0, 0, 95, 0, 95, 0, 1000000, Long.MAX_VALUE, grid);
      assertTrue(weighted.reachedGoal());
      assertEquals(optimal, pathCost(exact), 1e-6);
      assertTrue(pathCost(weighted) <= optimal * 1.1 + 1e-6, "a weight of 1.1 may cost at most 10% more");
      assertTrue(weighted.expanded() <= exact.expanded(), "the weighted search must not expand more nodes");
   }

   @Test
   void unreachableGoalReturnsTheNearestSpot() {
      Grid grid = new Grid(32);
      for (int z = 0; z < 32; z++) {
         grid.wall(16, z);
      }

      PathSearch search = exact();
      int steps = search.search(4, 0, 10, 28, 0, 10, 0, 100000, Long.MAX_VALUE, grid);
      assertFalse(search.reachedGoal());
      assertFalse(search.outOfBudget());
      assertTrue(steps > 0);
      assertEquals(15, search.stepX(steps - 1), "the path should end against the wall, as close to the goal as it gets");
      assertEquals(10, search.stepZ(steps - 1));
   }

   @Test
   void nodeBudgetStopsTheSearchWithAPartialPath() {
      Grid grid = new Grid(128);
      PathSearch search = exact();
      int steps = search.search(0, 0, 0, 120, 0, 120, 0, 25, Long.MAX_VALUE, grid);
      assertTrue(search.outOfBudget());
      assertFalse(search.reachedGoal());
      assertEquals(25, search.expanded());
      assertTrue(steps > 0, "a partial path toward the goal is still returned");
      assertTrue(search.stepX(steps - 1) > 0 && search.stepZ(steps - 1) > 0);
   }

   @Test
   void anExpiredDeadlineStopsTheSearch() {
      Grid grid = new Grid(128);
      PathSearch search = exact();
      search.search(0, 0, 0, 120, 0, 120, 0, 1000000, System.nanoTime() - 1L, grid);
      assertTrue(search.outOfBudget());
      assertTrue(search.expanded() <= 32, "the clock is read every 32 expansions");
   }

   @Test
   void goalRadiusAcceptsTheNeighbourhood() {
      Grid grid = new Grid(32);
      grid.wall(20, 20);
      PathSearch search = exact();
      search.search(2, 0, 2, 20, 0, 20, 1, 100000, Long.MAX_VALUE, grid);
      assertTrue(search.reachedGoal(), "a walled-in goal is reached by standing next to it");
   }

   @Test
   void aCheaperRouteFoundLaterReplacesTheFirst() {
      // 0 -> 1 directly costs 10; 0 -> 2 -> 1 costs 2. Node 1 is first seen at cost 10 and must be improved in place.
      PathSearch.MoveGenerator graph = (x, y, z, sink) -> {
         if (x == 0) {
            sink.offer(1, 0, 0, 10.0);
            sink.offer(2, 0, 0, 1.0);
         } else if (x == 2) {
            sink.offer(1, 0, 0, 1.0);
         } else if (x == 1) {
            sink.offer(9, 0, 0, 1.0);
         }
      };
      PathSearch search = new PathSearch().tune(0.0, 0.0, 0.0);
      int steps = search.search(0, 0, 0, 9, 0, 0, 0, 1000, Long.MAX_VALUE, graph);
      assertTrue(search.reachedGoal());
      assertEquals(3, steps);
      assertEquals(2, search.stepX(0));
      assertEquals(3.0, pathCost(search), 1e-9);
      assertEquals(4, search.visited(), "an improved node is updated, not duplicated");
   }

   @Test
   void searchesAreIndependentOfEachOther() {
      Grid grid = new Grid(64);
      PathSearch search = exact();
      search.search(0, 0, 0, 60, 0, 60, 0, 100000, Long.MAX_VALUE, grid);
      int steps = search.search(10, 0, 10, 13, 0, 10, 0, 100000, Long.MAX_VALUE, grid);
      assertEquals(3, steps);
      assertEquals(3.0, pathCost(search), 1e-9);
      assertTrue(search.visited() < 64, "nothing from the first search may leak into the second");
   }

   @Test
   void startAtTheGoalIsAnEmptyPath() {
      PathSearch search = exact();
      assertEquals(0, search.search(5, 0, 5, 5, 0, 5, 0, 100, Long.MAX_VALUE, new Grid(16)));
      assertTrue(search.reachedGoal());
   }

   @Test
   void packingKeepsNegativeCoordinatesApart() {
      long a = PathSearch.pack(-1, -64, -1);
      long b = PathSearch.pack(-1, -64, 0);
      long c = PathSearch.pack(0, -64, -1);
      long d = PathSearch.pack(-1, -63, -1);
      assertTrue(a != b && a != c && a != d && b != c);
      assertEquals(PathSearch.pack(30000000 - 1, 319, -30000000), PathSearch.pack(30000000 - 1, 319, -30000000));
   }
}
