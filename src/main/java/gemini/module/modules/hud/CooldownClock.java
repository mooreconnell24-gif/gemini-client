package gemini.module.modules.hud;

/**
 * Seconds left on a cooldown, worked out from the fraction the game reports. The client only learns "this much of
 * the cooldown is left" (1 at the start, falling to 0), and that fraction falls in a straight line, so the time left
 * is the fraction divided by how fast it falls. The rate is measured from the first sample to the latest one, and is
 * trusted once the fraction has fallen a little (short spans give noisy rates). Pure and unit tested.
 */
public final class CooldownClock {
   /** How far the fraction must fall before the rate is trusted. */
   static final double MIN_DROP = 0.03;
   private double firstFraction = Double.NaN;
   private long firstMs;
   private double lastFraction = Double.NaN;

   public void reset() {
      this.firstFraction = Double.NaN;
      this.lastFraction = Double.NaN;
   }

   /**
    * Records the fraction left at {@code nowMs} and returns the seconds left, or NaN while there is not yet enough
    * to measure the rate. A fraction that rises (a new cooldown) starts the measurement again.
    */
   public double sample(double fraction, long nowMs) {
      boolean rose = !Double.isNaN(this.lastFraction) && fraction > this.lastFraction + 1.0E-6;
      this.lastFraction = fraction;
      if (Double.isNaN(this.firstFraction) || rose) {
         this.firstFraction = fraction;
         this.firstMs = nowMs;
         return Double.NaN;
      }

      double drop = this.firstFraction - fraction;
      long elapsed = nowMs - this.firstMs;
      if (drop < MIN_DROP || elapsed <= 0L) {
         return Double.NaN;
      }

      double perSecond = drop / (elapsed / 1000.0);
      return Math.max(0.0, fraction / perSecond);
   }
}
