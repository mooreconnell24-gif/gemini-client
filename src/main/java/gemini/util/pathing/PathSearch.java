package gemini.util.pathing;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;

/**
 * A reusable, allocation-free A* over integer block coordinates.
 *
 * <p>What a "move" is and what it costs is supplied by a {@link MoveGenerator}; this class only does the search.
 * Nodes live in parallel primitive arrays (an arena) that are reused from one search to the next, the open set is
 * a binary heap of node indices with decrease-key, and visited positions are found through one
 * {@code long -> int} map. After the arena has grown to the size a search needs, a search allocates nothing: no
 * node objects, no position objects, no duplicate heap entries for improved nodes.
 *
 * <p>The heuristic is the octile distance (the exact cost of 8-connected movement on open ground) plus a per-block
 * charge for the height difference, scaled by a small weight. It is tighter than straight-line distance, so fewer
 * nodes are expanded for the same path. Ties on {@code f} go to the node nearer the goal.
 *
 * <p>A search stops at the goal, when the open set runs dry, or when the node or time budget is spent. In the last
 * two cases the result is the path to the explored node nearest the goal, so the caller still makes progress and
 * can search again from there.
 *
 * <p>Not thread-safe: one instance belongs to one thread.
 */
public final class PathSearch {
   /** Diagonal step cost; also used by the heuristic. */
   public static final double DIAGONAL = 1.414;
   private static final int NO_NODE = -1;
   private static final int NOT_IN_HEAP = -1;
   private static final int CLOSED = -2;
   private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
   private int[] nodeX = new int[1024];
   private int[] nodeY = new int[1024];
   private int[] nodeZ = new int[1024];
   private int[] parent = new int[1024];
   private double[] g = new double[1024];
   private double[] h = new double[1024];
   /** Position of the node in {@link #heap}, {@link #NOT_IN_HEAP} or {@link #CLOSED}. */
   private int[] heapSlot = new int[1024];
   private int nodes;
   private int[] heap = new int[1024];
   private int heapSize;
   private int goalX;
   private int goalY;
   private int goalZ;
   private double weight = 1.1;
   private double ascendCost = 0.65;
   private double descendCost = 0.3;
   private int current = NO_NODE;
   private final Sink sink = this::offer;
   private int[] path = new int[0];
   private int pathLength;
   private int expanded;
   private boolean reachedGoal;
   private boolean outOfBudget;

   public PathSearch() {
      this.index.defaultReturnValue(NO_NODE);
   }

   /** Where a {@link MoveGenerator} reports the moves out of a position. */
   @FunctionalInterface
   public interface Sink {
      /** A move from the position being expanded to {@code x, y, z} costing {@code cost} (must be positive). */
      void offer(int x, int y, int z, double cost);
   }

   /** Produces the moves available from one position. */
   @FunctionalInterface
   public interface MoveGenerator {
      void expand(int x, int y, int z, Sink sink);
   }

   /**
    * Heuristic tuning. {@code weight} above 1 trades a little path quality for speed; {@code ascend} and
    * {@code descend} are the least extra cost of gaining or losing one block of height.
    */
   public PathSearch tune(double weight, double ascend, double descend) {
      this.weight = weight;
      this.ascendCost = ascend;
      this.descendCost = descend;
      return this;
   }

   /**
    * Searches from the start to within {@code goalRadius} blocks (Manhattan) of the goal.
    *
    * @param maxNodes   stop after expanding this many nodes
    * @param deadline   stop once {@link System#nanoTime()} passes this value; {@link Long#MAX_VALUE} for no limit
    * @return the number of steps in the path, readable through {@link #stepX(int)} and friends; 0 when the start is
    *         already the best position found
    */
   public int search(int startX, int startY, int startZ, int goalX, int goalY, int goalZ, int goalRadius, int maxNodes, long deadline, MoveGenerator moves) {
      this.reset(goalX, goalY, goalZ);
      int start = this.newNode(startX, startY, startZ, NO_NODE, 0.0);
      this.index.put(pack(startX, startY, startZ), start);
      this.push(start);
      int best = start;

      while (this.heapSize > 0) {
         if (this.expanded >= maxNodes || (this.expanded & 31) == 31 && System.nanoTime() >= deadline) {
            this.outOfBudget = true;
            break;
         }

         int node = this.pop();
         this.heapSlot[node] = CLOSED;
         this.expanded++;
         if (this.h[node] < this.h[best]) {
            best = node;
         }

         int x = this.nodeX[node];
         int y = this.nodeY[node];
         int z = this.nodeZ[node];
         if (Math.abs(x - goalX) + Math.abs(y - goalY) + Math.abs(z - goalZ) <= goalRadius) {
            this.reachedGoal = true;
            best = node;
            break;
         }

         this.current = node;
         moves.expand(x, y, z, this.sink);
      }

      this.current = NO_NODE;
      this.buildPath(best);
      return this.pathLength;
   }

   private void offer(int x, int y, int z, double cost) {
      int from = this.current;
      double candidate = this.g[from] + cost;
      long key = pack(x, y, z);
      int existing = this.index.get(key);
      if (existing == NO_NODE) {
         int node = this.newNode(x, y, z, from, candidate);
         this.index.put(key, node);
         this.push(node);
      } else if (candidate < this.g[existing]) {
         // A cheaper way to a known position. Re-opening a closed node keeps the search correct when the weighted
         // heuristic let it be closed early.
         this.g[existing] = candidate;
         this.parent[existing] = from;
         if (this.heapSlot[existing] >= 0) {
            this.siftUp(this.heapSlot[existing]);
         } else {
            this.push(existing);
         }
      }
   }

   private void reset(int goalX, int goalY, int goalZ) {
      this.index.clear();
      this.nodes = 0;
      this.heapSize = 0;
      this.goalX = goalX;
      this.goalY = goalY;
      this.goalZ = goalZ;
      this.expanded = 0;
      this.reachedGoal = false;
      this.outOfBudget = false;
      this.pathLength = 0;
   }

   private int newNode(int x, int y, int z, int from, double cost) {
      int node = this.nodes++;
      if (node == this.nodeX.length) {
         int size = node * 2;
         this.nodeX = Arrays.copyOf(this.nodeX, size);
         this.nodeY = Arrays.copyOf(this.nodeY, size);
         this.nodeZ = Arrays.copyOf(this.nodeZ, size);
         this.parent = Arrays.copyOf(this.parent, size);
         this.g = Arrays.copyOf(this.g, size);
         this.h = Arrays.copyOf(this.h, size);
         this.heapSlot = Arrays.copyOf(this.heapSlot, size);
      }

      this.nodeX[node] = x;
      this.nodeY[node] = y;
      this.nodeZ[node] = z;
      this.parent[node] = from;
      this.g[node] = cost;
      this.h[node] = this.heuristic(x, y, z);
      this.heapSlot[node] = NOT_IN_HEAP;
      return node;
   }

   /** Octile distance on the horizontal plane plus the least cost of the height difference, times the weight. */
   double heuristic(int x, int y, int z) {
      int dx = Math.abs(x - this.goalX);
      int dz = Math.abs(z - this.goalZ);
      int dy = this.goalY - y;
      int straight = Math.abs(dx - dz);
      int diagonal = Math.min(dx, dz);
      double vertical = dy > 0 ? dy * this.ascendCost : -dy * this.descendCost;
      return (straight + diagonal * DIAGONAL + vertical) * this.weight;
   }

   // ------------------------------------------------------------------ indexed binary heap

   private boolean before(int a, int b) {
      double fa = this.g[a] + this.h[a];
      double fb = this.g[b] + this.h[b];
      return fa < fb || fa == fb && this.h[a] < this.h[b];
   }

   private void push(int node) {
      if (this.heapSize == this.heap.length) {
         this.heap = Arrays.copyOf(this.heap, this.heapSize * 2);
      }

      this.heap[this.heapSize] = node;
      this.heapSlot[node] = this.heapSize;
      this.siftUp(this.heapSize++);
   }

   private int pop() {
      int top = this.heap[0];
      int last = this.heap[--this.heapSize];
      if (this.heapSize > 0) {
         this.heap[0] = last;
         this.heapSlot[last] = 0;
         this.siftDown(0);
      }

      return top;
   }

   private void siftUp(int slot) {
      int node = this.heap[slot];
      while (slot > 0) {
         int up = (slot - 1) >>> 1;
         int other = this.heap[up];
         if (!this.before(node, other)) {
            break;
         }

         this.heap[slot] = other;
         this.heapSlot[other] = slot;
         slot = up;
      }

      this.heap[slot] = node;
      this.heapSlot[node] = slot;
   }

   private void siftDown(int slot) {
      int node = this.heap[slot];
      int half = this.heapSize >>> 1;
      while (slot < half) {
         int child = 2 * slot + 1;
         int right = child + 1;
         if (right < this.heapSize && this.before(this.heap[right], this.heap[child])) {
            child = right;
         }

         int other = this.heap[child];
         if (!this.before(other, node)) {
            break;
         }

         this.heap[slot] = other;
         this.heapSlot[other] = slot;
         slot = child;
      }

      this.heap[slot] = node;
      this.heapSlot[node] = slot;
   }

   // ------------------------------------------------------------------ result

   private void buildPath(int end) {
      int length = 0;
      for (int node = end; this.parent[node] != NO_NODE; node = this.parent[node]) {
         length++;
      }

      if (this.path.length < length) {
         this.path = new int[Math.max(length, this.path.length * 2)];
      }

      int slot = length;
      for (int node = end; this.parent[node] != NO_NODE; node = this.parent[node]) {
         this.path[--slot] = node;
      }

      this.pathLength = length;
   }

   /** Number of steps in the last result (the start itself is not a step). */
   public int pathLength() {
      return this.pathLength;
   }

   public int stepX(int step) {
      return this.nodeX[this.path[step]];
   }

   public int stepY(int step) {
      return this.nodeY[this.path[step]];
   }

   public int stepZ(int step) {
      return this.nodeZ[this.path[step]];
   }

   /** Cost from the start to the given step of the last result. */
   public double stepCost(int step) {
      return this.g[this.path[step]];
   }

   /** True when the last search ended at the goal rather than at the nearest explored position. */
   public boolean reachedGoal() {
      return this.reachedGoal;
   }

   /** True when the last search stopped because the node or time budget ran out. */
   public boolean outOfBudget() {
      return this.outOfBudget;
   }

   /** Nodes expanded by the last search. */
   public int expanded() {
      return this.expanded;
   }

   /** Distinct positions the last search touched. */
   public int visited() {
      return this.nodes;
   }

   /** Same packing as Minecraft's {@code BlockPos.asLong}: 26 bits x, 26 bits z, 12 bits y. */
   public static long pack(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | (long)y & 0xFFFL;
   }
}
