package gemini.util;

/**
 * Aims a projectile that follows Minecraft's tick physics, with no game classes involved.
 *
 * <p>Arrows and tridents ({@code AbstractArrow.tick}) move by their velocity first, then multiply it by the air drag
 * ({@code 0.99F}) and then subtract the gravity ({@code 0.05}). Started with velocity {@code u}, the position after
 * {@code n} ticks is therefore exactly
 *
 * <pre>
 * pos(n) = u * S(n) - y * g * G(n),   S(n) = (1 - d^n) / (1 - d),   G(n) = (n - S(n)) / (1 - d)
 * </pre>
 *
 * which is linear in {@code u}. The launch velocity is the aim direction times the launch speed plus whatever the
 * shooter's own movement adds ({@code Projectile.shootFromRotation}). For a target that moves at a constant velocity
 * the aim for a flight of {@code n} ticks is the direction of {@code R(n) = P + V n - w S(n) + y g G(n)}, and the
 * flight time is a root of {@code |R(n)| = speed * S(n)}: the smaller root is the low arc, the larger the high arc.
 * Solving that root directly is the fixed point of the usual "flight time, predicted position, solve again" loop.
 * {@link #simulate} replays the same physics tick by tick for wall checks and drawing.
 */
public final class BallisticSolver {
   /** {@code AbstractArrow.getDefaultGravity()} (arrows, spectral arrows, tridents). */
   public static final double ARROW_GRAVITY = 0.05;
   /** {@code AbstractArrow.getAirDrag()}: the float 0.99F widened, as the game does. */
   public static final double ARROW_DRAG = 0.99F;
   /** {@code BowItem}: launch speed is the draw power times this. */
   public static final double BOW_SPEED = 3.0;
   /** {@code CrossbowItem} arrow power. */
   public static final double CROSSBOW_SPEED = 3.15;
   /** {@code TridentItem} throw power. */
   public static final double TRIDENT_SPEED = 2.5;
   /** Arrows and tridents start this far below the shooter's eyes. */
   public static final double LAUNCH_DROP = 0.10000000149011612;

   public static final int LOW = 0;
   public static final int HIGH = 1;

   private static final double SCAN_STEP = 0.5;
   private static final int BISECTIONS = 40;

   private BallisticSolver() {
   }

   /** {@code BowItem.getPowerForTime}: 0..1 draw power after {@code ticks} of drawing. */
   public static double bowPower(int ticks) {
      double f = ticks / 20.0;
      f = (f * f + f * 2.0) / 3.0;
      return Math.min(1.0, f);
   }

   /** A solved shot: aim direction, yaw/pitch, flight ticks and the (predicted) point it reaches. */
   public static final class Solution {
      public double dirX;
      public double dirY;
      public double dirZ;
      public float yaw;
      public float pitch;
      public double ticks;
      /** Impact point relative to the launch point. */
      public double hitX;
      public double hitY;
      public double hitZ;
   }

   /** Distance the drag lets a unit initial velocity travel in {@code n} ticks. */
   static double s(double n, double drag) {
      if (Math.abs(1.0 - drag) < 1.0E-12) {
         return n;
      }

      return (1.0 - Math.pow(drag, n)) / (1.0 - drag);
   }

   /** Accumulated gravity drop (in units of the gravity) after {@code n} ticks. */
   static double gTerm(double n, double drag) {
      if (Math.abs(1.0 - drag) < 1.0E-12) {
         return n * (n - 1.0) * 0.5;
      }

      return (n - s(n, drag)) / (1.0 - drag);
   }

   /**
    * Solves a shot. Positions are relative to the launch point; {@code vx..vz} is the target's velocity per tick,
    * {@code wx..wz} the velocity the shooter adds to the projectile. {@code arc} is {@link #LOW} or {@link #HIGH}.
    * Returns false when no arc reaches the target within {@code maxTicks} (out of range).
    */
   public static boolean solve(
      double px,
      double py,
      double pz,
      double vx,
      double vy,
      double vz,
      double wx,
      double wy,
      double wz,
      double speed,
      double gravity,
      double drag,
      int arc,
      int maxTicks,
      Solution out
   ) {
      if (speed <= 0.0 || maxTicks <= 0) {
         return false;
      }

      double previousN = 0.0;
      double previousF = f(previousN, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
      double lowRoot = Double.NaN;
      double highRoot = Double.NaN;
      for (double n = SCAN_STEP; n <= maxTicks + 1.0E-9; n += SCAN_STEP) {
         double value = f(n, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
         if (Double.isNaN(lowRoot)) {
            if (previousF > 0.0 && value <= 0.0) {
               lowRoot = bisect(previousN, n, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
               if (arc == LOW) {
                  break;
               }
            }
         } else if (previousF <= 0.0 && value > 0.0) {
            highRoot = bisect(previousN, n, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
            break;
         }

         previousN = n;
         previousF = value;
      }

      double n = arc == HIGH ? highRoot : lowRoot;
      if (Double.isNaN(n)) {
         return false;
      }

      double sn = s(n, drag);
      double gn = gTerm(n, drag);
      double rx = px + vx * n - wx * sn;
      double ry = py + vy * n - wy * sn + gravity * gn;
      double rz = pz + vz * n - wz * sn;
      double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
      if (length < 1.0E-9) {
         return false;
      }

      out.dirX = rx / length;
      out.dirY = ry / length;
      out.dirZ = rz / length;
      out.yaw = yawOf(out.dirX, out.dirZ);
      out.pitch = pitchOf(out.dirX, out.dirY, out.dirZ);
      out.ticks = n;
      out.hitX = px + vx * n;
      out.hitY = py + vy * n;
      out.hitZ = pz + vz * n;
      return true;
   }

   private static double f(
      double n, double px, double py, double pz, double vx, double vy, double vz, double wx, double wy, double wz, double speed, double gravity, double drag
   ) {
      double sn = s(n, drag);
      double gn = gTerm(n, drag);
      double rx = px + vx * n - wx * sn;
      double ry = py + vy * n - wy * sn + gravity * gn;
      double rz = pz + vz * n - wz * sn;
      return Math.sqrt(rx * rx + ry * ry + rz * rz) - speed * sn;
   }

   private static double bisect(
      double lo, double hi, double px, double py, double pz, double vx, double vy, double vz, double wx, double wy, double wz, double speed, double gravity, double drag
   ) {
      double fLo = f(lo, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
      for (int i = 0; i < BISECTIONS; i++) {
         double mid = (lo + hi) * 0.5;
         double fMid = f(mid, px, py, pz, vx, vy, vz, wx, wy, wz, speed, gravity, drag);
         if (fMid > 0.0 == fLo > 0.0) {
            lo = mid;
            fLo = fMid;
         } else {
            hi = mid;
         }
      }

      return (lo + hi) * 0.5;
   }

   /** Minecraft yaw (degrees) of a horizontal direction. */
   public static float yawOf(double dx, double dz) {
      return (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
   }

   /** Minecraft pitch (degrees, negative is up) of a direction. */
   public static float pitchOf(double dx, double dy, double dz) {
      return (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
   }

   /**
    * Replays the tick physics from {@code start} with launch velocity {@code u}, writing up to {@code points}
    * positions (x, y, z triples, starting with the launch point) into {@code out}. Returns how many were written.
    */
   public static int simulate(
      double sx, double sy, double sz, double ux, double uy, double uz, double gravity, double drag, int points, double[] out
   ) {
      int count = Math.min(points, out.length / 3);
      double x = sx;
      double y = sy;
      double z = sz;
      double vx = ux;
      double vy = uy;
      double vz = uz;
      for (int i = 0; i < count; i++) {
         out[i * 3] = x;
         out[i * 3 + 1] = y;
         out[i * 3 + 2] = z;
         x += vx;
         y += vy;
         z += vz;
         vx *= drag;
         vy *= drag;
         vz *= drag;
         vy -= gravity;
      }

      return count;
   }

   /**
    * Position after a fractional number of ticks along the real (piecewise straight) path: the whole ticks are
    * simulated and the last part of a tick is a straight step, as the projectile moves within one tick.
    */
   public static void positionAt(double ux, double uy, double uz, double gravity, double drag, double ticks, double[] out3) {
      int whole = (int)Math.floor(ticks);
      double x = 0.0;
      double y = 0.0;
      double z = 0.0;
      double vx = ux;
      double vy = uy;
      double vz = uz;
      for (int i = 0; i < whole; i++) {
         x += vx;
         y += vy;
         z += vz;
         vx *= drag;
         vy *= drag;
         vz *= drag;
         vy -= gravity;
      }

      double frac = ticks - whole;
      out3[0] = x + vx * frac;
      out3[1] = y + vy * frac;
      out3[2] = z + vz * frac;
   }
}
