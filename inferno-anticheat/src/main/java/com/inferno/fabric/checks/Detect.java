package com.inferno.fabric.checks;

import java.util.Collection;

/**
 * Pure maths shared by the LiquidBounce coverage checks. Nothing here touches Minecraft classes, so
 * the numbers are covered by plain unit tests (DetectTest).
 */
public final class Detect {
   /** Gravity and air drag per tick for a player (LivingEntity#travel). */
   public static final double GRAVITY = 0.08;
   public static final double AIR_DRAG = 0.98;
   /** Vanilla jump impulse (Attributes.JUMP_STRENGTH default). */
   public static final double JUMP_IMPULSE = 0.42;
   /** Ground speed of a walking player: 0.1 speed attribute on normal blocks (0.6 * 0.91 friction). */
   public static final double WALK_SPEED = 0.21585;
   /** Sprinting multiplies the speed attribute by 1.3. */
   public static final double SPRINT_SPEED = WALK_SPEED * 1.3;

   private Detect() {
   }

   /** Highest point (blocks above take-off) a vanilla jump with the given impulse reaches. */
   public static double jumpApex(double impulse) {
      double y = 0.0;
      double vy = impulse;
      double max = 0.0;

      for (int i = 0; i < 40 && vy > 0.0; i++) {
         y += vy;
         max = Math.max(max, y);
         vy = (vy - GRAVITY) * AIR_DRAG;
      }

      return max;
   }

   /** Vertical speed after {@code ticks} ticks of free fall from rest. */
   public static double fallSpeedAfter(int ticks) {
      double vy = 0.0;

      for (int i = 0; i < ticks; i++) {
         vy = (vy - GRAVITY) * AIR_DRAG;
      }

      return vy;
   }

   /** Ground speed while sneaking: input is scaled by the sneaking-speed attribute (0.3 by default). */
   public static double sneakGroundSpeed(double sneakingSpeedAttribute, boolean sprinting) {
      double factor = Math.max(0.0, Math.min(1.0, sneakingSpeedAttribute));
      return (sprinting ? SPRINT_SPEED : WALK_SPEED) * factor;
   }

   /** Horizontal speed a player keeps after one ground tick with no movement input (friction only). */
   public static double coastSpeed(double previous, double blockFriction) {
      return previous * blockFriction * 0.91;
   }

   public static double mean(Collection<? extends Number> values) {
      if (values.isEmpty()) {
         return 0.0;
      }

      double sum = 0.0;

      for (Number v : values) {
         sum += v.doubleValue();
      }

      return sum / values.size();
   }

   /** Sample standard deviation (n - 1). */
   public static double stdDev(Collection<? extends Number> values) {
      if (values.size() < 2) {
         return 0.0;
      }

      double mean = mean(values);
      double sum = 0.0;

      for (Number v : values) {
         double d = v.doubleValue() - mean;
         sum += d * d;
      }

      return Math.sqrt(sum / (values.size() - 1));
   }

   /**
    * How far event times stray from the best straight line through them (least squares), in the same
    * unit. A bot clicking on a fixed schedule only strays by its network jitter; a person's timing
    * errors add up like a random walk, so they stray by tens of milliseconds within a dozen clicks.
    */
   public static double scheduleDeviation(java.util.List<Long> times) {
      int n = times.size();
      if (n < 3) {
         return Double.POSITIVE_INFINITY;
      }

      double t0 = times.get(0);
      double meanI = (n - 1) / 2.0;
      double meanT = 0.0;

      for (long t : times) {
         meanT += t - t0;
      }

      meanT /= n;
      double sxy = 0.0;
      double sxx = 0.0;

      for (int i = 0; i < n; i++) {
         sxy += (i - meanI) * (times.get(i) - t0 - meanT);
         sxx += (i - meanI) * (i - meanI);
      }

      double slope = sxy / sxx;
      double sum = 0.0;

      for (int i = 0; i < n; i++) {
         double r = times.get(i) - t0 - (meanT + slope * (i - meanI));
         sum += r * r;
      }

      return Math.sqrt(sum / (n - 2));
   }

   /**
    * Timer balance step: how far ahead of real time a client is after one more tick. Each tick should
    * take {@code expectedMs}; a client that runs faster gains the difference. The balance never drops
    * below {@code floorMs} so that a lag spike can't be "saved up" and spent later as a speed burst.
    */
   public static double timerBalance(double balance, long gapMs, double expectedMs, double floorMs) {
      return Math.max(floorMs, balance + (expectedMs - gapMs));
   }

   /** True when a vanilla client could not send this many packets of one kind in one of its ticks. */
   public static boolean tooManyMoves(int moves, boolean teleportAccepted) {
      // One movement packet per tick (LocalPlayer#sendPosition) plus one more when the client answers
      // a server teleport (ClientPacketListener#handleMovePlayer).
      return moves > (teleportAccepted ? 2 : 1);
   }

   /** Lower-case words of a client brand or channel namespace. */
   public static String[] words(String text) {
      return text == null ? new String[0] : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+");
   }

   /** Known cheat clients, matched as whole words of the brand / channel namespace. */
   private static final java.util.Set<String> CHEAT_WORDS = java.util.Set.of(
      "liquidbounce", "wurst", "meteor", "aristois", "impact", "rusherhack", "inertia", "konas", "phobos", "salhack",
      "kami", "sigma", "novoline", "vape", "bleachhack", "thunderhack", "nursultan", "wexside", "boze", "baritone"
   );

   /** The cheat-client word found in {@code text}, or null. "meteor-client" and "LiquidBounce 1.0" match. */
   public static String cheatWord(String text) {
      for (String w : words(text)) {
         if (CHEAT_WORDS.contains(w)) {
            return w;
         }
      }

      String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
      return lower.contains("liquidbounce") ? "liquidbounce" : null;
   }

   /** Ratio of how long a block took to break to how long it should take (1.0 = exactly vanilla). */
   public static double breakRatio(long tookMs, float progressPerTick) {
      if (progressPerTick <= 0.0F) {
         return Double.POSITIVE_INFINITY;
      }

      double ticks = Math.ceil(1.0 / progressPerTick);
      return tookMs / (ticks * 50.0);
   }
}
