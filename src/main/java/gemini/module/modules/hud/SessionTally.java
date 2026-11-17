package gemini.module.modules.hud;

/**
 * Counts gained since a session started, from counters that only ever go up (the player's statistics).
 *
 * <p>The first reading after {@link #reset} becomes the baseline, so a session starts at zero whatever the
 * lifetime totals are. A counter that drops below its baseline was reset underneath us (a new world, a stats
 * wipe): its baseline follows it down instead of reporting a negative gain.
 */
public final class SessionTally {
   private final long[] base;
   private final long[] latest;
   private boolean primed;
   private long startedMs;

   public SessionTally(int counters) {
      this.base = new long[counters];
      this.latest = new long[counters];
   }

   public int size() {
      return this.base.length;
   }

   /** Starts a new session at {@code nowMs}; the next {@link #update} becomes the baseline. */
   public void reset(long nowMs) {
      this.primed = false;
      this.startedMs = nowMs;
      java.util.Arrays.fill(this.base, 0L);
      java.util.Arrays.fill(this.latest, 0L);
   }

   public boolean primed() {
      return this.primed;
   }

   /** Feeds one reading of every counter, in the same order each time. Extra values are ignored. */
   public void update(long[] values) {
      int n = Math.min(values.length, this.base.length);
      if (!this.primed) {
         System.arraycopy(values, 0, this.base, 0, n);
         this.primed = true;
      }

      for (int i = 0; i < n; i++) {
         if (values[i] < this.base[i]) {
            this.base[i] = values[i];
         }

         this.latest[i] = values[i];
      }
   }

   /** How much counter {@code index} went up this session. */
   public long gained(int index) {
      return this.primed ? this.latest[index] - this.base[index] : 0L;
   }

   /** Milliseconds since the session started. */
   public long elapsed(long nowMs) {
      return Math.max(0L, nowMs - this.startedMs);
   }

   /** The gain extrapolated to one hour; NaN during the first minute, when a rate means little. */
   public double perHour(int index, long nowMs) {
      long elapsed = this.elapsed(nowMs);
      return elapsed < 60_000L ? Double.NaN : this.gained(index) * 3_600_000.0 / elapsed;
   }
}
