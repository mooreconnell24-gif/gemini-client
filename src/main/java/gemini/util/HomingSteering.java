package gemini.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Steering for a homing projectile, with no game classes involved: pick a direction toward the target that is clear
 * of obstacles, then turn the current velocity toward it by at most a set angle per tick.
 *
 * <p>Obstacle avoidance: the straight line to the target is tried first. If something is in the way within the look
 * ahead, directions on cones of growing angle around the desired direction are tried and, of the first ring that
 * has clear directions, the one closest to the current heading is taken (so the path does not flicker between
 * sides). Narrow cones (up to 40 degrees) come first, enough to slip past a post or a mob. When those are all
 * blocked the obstacle is a wall, and wide cones would only slide along it, so a coarse A* over the block grid
 * around the projectile plans a way round (to the target, or to the reachable cell nearest it) and the projectile
 * heads for the farthest waypoint it can see; the wide cones are the last resort. The result is never a zero
 * vector.
 */
public final class HomingSteering {
   /** Whether a straight move is free of blocks and entities. The direction is a unit vector. */
   @FunctionalInterface
   public interface Probe {
      boolean clear(double x, double y, double z, double dx, double dy, double dz, double length);
   }

   /** Whether the block cell at the given coordinates stops the projectile. */
   @FunctionalInterface
   public interface Solid {
      boolean solid(int x, int y, int z);
   }

   private static final int CONE_STEP = 10;
   /** Cones up to this angle are tried before the grid planner: enough to slip past a post or a mob. */
   private static final int NEAR_CONE = 40;
   private static final int CONE_MAX = 170;
   private static final int RING = 12;
   private static final int GRID_RADIUS = 8;
   private static final int MAX_NODES = 3000;
   private static final double[] RING_COS = new double[RING];
   private static final double[] RING_SIN = new double[RING];

   static {
      for (int i = 0; i < RING; i++) {
         RING_COS[i] = Math.cos(Math.PI * 2.0 * i / RING);
         RING_SIN[i] = Math.sin(Math.PI * 2.0 * i / RING);
      }
   }

   private HomingSteering() {
   }

   /**
    * The new velocity: toward the target ({@code tx..tz}) at {@code speed} blocks per tick, around obstacles found by
    * {@code probe} within {@code lookAhead} blocks, turning at most {@code maxTurnDegrees} from the current velocity.
    * {@code solid} may be null to skip the A* fallback.
    */
   public static double[] steer(
      double px,
      double py,
      double pz,
      double vx,
      double vy,
      double vz,
      double tx,
      double ty,
      double tz,
      double speed,
      double maxTurnDegrees,
      double lookAhead,
      Probe probe,
      Solid solid
   ) {
      double[] want = chooseDirection(px, py, pz, vx, vy, vz, tx, ty, tz, lookAhead, probe, solid);
      double[] turned = limitTurn(vx, vy, vz, want[0], want[1], want[2], maxTurnDegrees);
      double s = Math.max(0.01, speed);
      turned[0] *= s;
      turned[1] *= s;
      turned[2] *= s;
      return turned;
   }

   /** The unit direction to fly in (before the turn limit). Never zero. */
   public static double[] chooseDirection(
      double px, double py, double pz, double vx, double vy, double vz, double tx, double ty, double tz, double lookAhead, Probe probe, Solid solid
   ) {
      double[] heading = unitOr(vx, vy, vz, 0.0, 0.0, 1.0);
      double dx = tx - px;
      double dy = ty - py;
      double dz = tz - pz;
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (distance < 1.0E-6) {
         return heading;
      }

      double[] desired = {dx / distance, dy / distance, dz / distance};
      if (probe == null || probe.clear(px, py, pz, desired[0], desired[1], desired[2], Math.min(lookAhead, distance))) {
         return desired;
      }

      // Two unit vectors perpendicular to the desired direction span the cones.
      double[] u = perpendicular(desired);
      double[] w = cross(desired, u);
      boolean gridTried = false;
      for (int angle = CONE_STEP; angle <= CONE_MAX; angle += CONE_STEP) {
         if (angle > NEAR_CONE && !gridTried && solid != null) {
            // A small swerve did not do it: this is a wall, not a post. Plan around it on the block grid before
            // trying wide turns, which would only slide along the wall.
            gridTried = true;
            double[] viaGrid = gridDirection(px, py, pz, tx, ty, tz, probe, solid);
            if (viaGrid != null) {
               return viaGrid;
            }
         }


         double rad = Math.toRadians(angle);
         double c = Math.cos(rad);
         double s = Math.sin(rad);
         double[] best = null;
         double bestDot = Double.NEGATIVE_INFINITY;
         for (int k = 0; k < RING; k++) {
            double ox = (u[0] * RING_COS[k] + w[0] * RING_SIN[k]) * s;
            double oy = (u[1] * RING_COS[k] + w[1] * RING_SIN[k]) * s;
            double oz = (u[2] * RING_COS[k] + w[2] * RING_SIN[k]) * s;
            double cx = desired[0] * c + ox;
            double cy = desired[1] * c + oy;
            double cz = desired[2] * c + oz;
            if (probe.clear(px, py, pz, cx, cy, cz, lookAhead)) {
               double dot = cx * heading[0] + cy * heading[1] + cz * heading[2];
               if (dot > bestDot) {
                  bestDot = dot;
                  best = new double[]{cx, cy, cz};
               }
            }
         }

         if (best != null) {
            return unitOr(best[0], best[1], best[2], desired[0], desired[1], desired[2]);
         }
      }

      if (solid != null && !gridTried) {
         double[] viaGrid = gridDirection(px, py, pz, tx, ty, tz, probe, solid);
         if (viaGrid != null) {
            return viaGrid;
         }
      }

      return desired;
   }

   /**
    * Turns the unit-or-not vector {@code (vx, vy, vz)} toward the unit vector {@code want} by at most
    * {@code maxDegrees}; returns a unit vector. With no current velocity the wanted direction is returned.
    */
   public static double[] limitTurn(double vx, double vy, double vz, double wx, double wy, double wz, double maxDegrees) {
      double[] want = unitOr(wx, wy, wz, 0.0, 0.0, 1.0);
      double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
      if (length < 1.0E-9) {
         return want;
      }

      double[] from = {vx / length, vy / length, vz / length};
      double dot = Math.max(-1.0, Math.min(1.0, from[0] * want[0] + from[1] * want[1] + from[2] * want[2]));
      double angle = Math.acos(dot);
      double max = Math.toRadians(Math.max(0.0, maxDegrees));
      if (angle <= max) {
         return want;
      }

      // Rotate "from" toward "want" in their common plane by "max".
      double ox = want[0] - from[0] * dot;
      double oy = want[1] - from[1] * dot;
      double oz = want[2] - from[2] * dot;
      double oLength = Math.sqrt(ox * ox + oy * oy + oz * oz);
      double[] ortho;
      if (oLength < 1.0E-9) {
         // Straight behind: any perpendicular turns away.
         ortho = perpendicular(from);
      } else {
         ortho = new double[]{ox / oLength, oy / oLength, oz / oLength};
      }

      double c = Math.cos(max);
      double s = Math.sin(max);
      return unitOr(from[0] * c + ortho[0] * s, from[1] * c + ortho[1] * s, from[2] * c + ortho[2] * s, from[0], from[1], from[2]);
   }

   /**
    * Coarse A* over the block grid (cells within {@value #GRID_RADIUS} of the projectile, 26 neighbours, no corner
    * cutting) toward the cell nearest the target; returns the direction to the farthest waypoint the probe can
    * reach in a straight line, or null when there is no way.
    */
   static double[] gridDirection(double px, double py, double pz, double tx, double ty, double tz, Probe probe, Solid solid) {
      int sx = floor(px);
      int sy = floor(py);
      int sz = floor(pz);
      int gx = sx + clamp(floor(tx) - sx, -GRID_RADIUS + 1, GRID_RADIUS - 1);
      int gy = sy + clamp(floor(ty) - sy, -GRID_RADIUS + 1, GRID_RADIUS - 1);
      int gz = sz + clamp(floor(tz) - sz, -GRID_RADIUS + 1, GRID_RADIUS - 1);
      List<int[]> path = aStar(sx, sy, sz, gx, gy, gz, floor(tx), floor(ty), floor(tz), solid);
      if (path == null || path.size() < 2) {
         return null;
      }

      for (int i = path.size() - 1; i >= 1; i--) {
         int[] cell = path.get(i);
         double cx = cell[0] + 0.5 - px;
         double cy = cell[1] + 0.5 - py;
         double cz = cell[2] + 0.5 - pz;
         double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
         if (length > 1.0E-6 && (probe == null || probe.clear(px, py, pz, cx / length, cy / length, cz / length, length) || i == 1)) {
            return new double[]{cx / length, cy / length, cz / length};
         }
      }

      return null;
   }

   /** A* from start to goal over free cells; the path includes both ends, or null. Visible for tests. */
   static List<int[]> aStar(int sx, int sy, int sz, int gx, int gy, int gz, Solid solid) {
      return aStar(sx, sy, sz, gx, gy, gz, gx, gy, gz, solid);
   }

   /**
    * A* toward the goal cell; when the goal cannot be reached (solid, or walled off) the path ends at the reached
    * cell closest to {@code (fx, fy, fz)}, the real target cell, which may lie outside the grid.
    */
   static List<int[]> aStar(int sx, int sy, int sz, int gx, int gy, int gz, int fx, int fy, int fz, Solid solid) {
      int size = GRID_RADIUS * 2 + 1;
      Map<Integer, Double> cost = new HashMap<>();
      Map<Integer, Integer> parent = new HashMap<>();
      PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
      int start = index(0, 0, 0, size);
      int goal = index(gx - sx, gy - sy, gz - sz, size);
      cost.put(start, 0.0);
      open.add(new double[]{heuristic(0, 0, 0, gx - sx, gy - sy, gz - sz), start});
      int expanded = 0;
      int closest = start;
      double closestDistance = heuristic(sx, sy, sz, fx, fy, fz);
      while (!open.isEmpty() && expanded < MAX_NODES) {
         double[] node = open.poll();
         int id = (int)node[1];
         if (id == goal) {
            return pathTo(id, parent, sx, sy, sz, size);
         }

         double known = cost.get(id);
         if (node[0] > known + heuristicOf(id, goal, size) + 1.0E-9) {
            continue;
         }

         expanded++;
         int[] rel = unindex(id, size);
         double toTarget = heuristic(sx + rel[0], sy + rel[1], sz + rel[2], fx, fy, fz);
         if (toTarget < closestDistance - 1.0E-9) {
            closestDistance = toTarget;
            closest = id;
         }

         for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
               for (int oz = -1; oz <= 1; oz++) {
                  if (ox == 0 && oy == 0 && oz == 0) {
                     continue;
                  }

                  int nx = rel[0] + ox;
                  int ny = rel[1] + oy;
                  int nz = rel[2] + oz;
                  if (Math.abs(nx) > GRID_RADIUS || Math.abs(ny) > GRID_RADIUS || Math.abs(nz) > GRID_RADIUS) {
                     continue;
                  }

                  if (!moveFree(sx + rel[0], sy + rel[1], sz + rel[2], ox, oy, oz, solid)) {
                     continue;
                  }

                  int next = index(nx, ny, nz, size);
                  double step = Math.sqrt(ox * ox + oy * oy + oz * oz);
                  double total = known + step;
                  Double previous = cost.get(next);
                  if (previous == null || total < previous - 1.0E-9) {
                     cost.put(next, total);
                     parent.put(next, id);
                     open.add(new double[]{total + heuristicOf(next, goal, size), next});
                  }
               }
            }
         }
      }

      return closest == start ? null : pathTo(closest, parent, sx, sy, sz, size);
   }

   private static List<int[]> pathTo(int id, Map<Integer, Integer> parent, int sx, int sy, int sz, int size) {
      List<int[]> path = new ArrayList<>();
      Integer at = id;
      while (at != null) {
         int[] rel = unindex(at, size);
         path.add(0, new int[]{sx + rel[0], sy + rel[1], sz + rel[2]});
         at = parent.get(at);
      }

      return path;
   }

   /** Every cell in the box spanned by a (diagonal) move must be free, so a move never cuts a corner. */
   private static boolean moveFree(int x, int y, int z, int ox, int oy, int oz, Solid solid) {
      for (int a = Math.min(0, ox); a <= Math.max(0, ox); a++) {
         for (int b = Math.min(0, oy); b <= Math.max(0, oy); b++) {
            for (int c = Math.min(0, oz); c <= Math.max(0, oz); c++) {
               if ((a != 0 || b != 0 || c != 0) && solid.solid(x + a, y + b, z + c)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private static int index(int x, int y, int z, int size) {
      return ((x + GRID_RADIUS) * size + (y + GRID_RADIUS)) * size + (z + GRID_RADIUS);
   }

   private static int[] unindex(int id, int size) {
      int z = id % size;
      int y = id / size % size;
      int x = id / (size * size);
      return new int[]{x - GRID_RADIUS, y - GRID_RADIUS, z - GRID_RADIUS};
   }

   private static double heuristicOf(int a, int b, int size) {
      int[] p = unindex(a, size);
      int[] q = unindex(b, size);
      return heuristic(p[0], p[1], p[2], q[0], q[1], q[2]);
   }

   private static double heuristic(int x1, int y1, int z1, int x2, int y2, int z2) {
      double dx = x2 - x1;
      double dy = y2 - y1;
      double dz = z2 - z1;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /**
    * Slab test: whether the segment from {@code (ox, oy, oz)} along the unit direction for {@code length} blocks
    * passes through the box.
    */
   public static boolean segmentHitsBox(
      double ox, double oy, double oz, double dx, double dy, double dz, double length, double minX, double minY, double minZ, double maxX, double maxY, double maxZ
   ) {
      double tMin = 0.0;
      double tMax = length;
      double[] o = {ox, oy, oz};
      double[] d = {dx, dy, dz};
      double[] lo = {minX, minY, minZ};
      double[] hi = {maxX, maxY, maxZ};
      for (int axis = 0; axis < 3; axis++) {
         if (Math.abs(d[axis]) < 1.0E-12) {
            if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
               return false;
            }
         } else {
            double t1 = (lo[axis] - o[axis]) / d[axis];
            double t2 = (hi[axis] - o[axis]) / d[axis];
            if (t1 > t2) {
               double t = t1;
               t1 = t2;
               t2 = t;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
               return false;
            }
         }
      }

      return true;
   }

   private static int floor(double value) {
      return (int)Math.floor(value);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double[] unitOr(double x, double y, double z, double fx, double fy, double fz) {
      double length = Math.sqrt(x * x + y * y + z * z);
      if (length < 1.0E-9 || Double.isNaN(length)) {
         double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
         return fl < 1.0E-9 ? new double[]{0.0, 0.0, 1.0} : new double[]{fx / fl, fy / fl, fz / fl};
      }

      return new double[]{x / length, y / length, z / length};
   }

   /** A unit vector perpendicular to the unit vector {@code v}. */
   private static double[] perpendicular(double[] v) {
      // Cross with the axis least aligned with v.
      double ax = Math.abs(v[0]);
      double ay = Math.abs(v[1]);
      double az = Math.abs(v[2]);
      double[] axis = ax <= ay && ax <= az ? new double[]{1.0, 0.0, 0.0} : ay <= az ? new double[]{0.0, 1.0, 0.0} : new double[]{0.0, 0.0, 1.0};
      double[] p = cross(v, axis);
      return unitOr(p[0], p[1], p[2], 0.0, 1.0, 0.0);
   }

   private static double[] cross(double[] a, double[] b) {
      return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }
}
