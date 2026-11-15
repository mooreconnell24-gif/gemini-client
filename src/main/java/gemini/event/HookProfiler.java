package gemini.event;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Rolling per-module hook timings for the live profiler HUD.
 *
 * <p>Each (module, hook) pair has a small ring of time buckets. {@link #record} adds one hook call to the current
 * bucket; {@link #advance} moves the ring on as the clock passes bucket boundaries, zeroing what falls out. The
 * reported figure is the mean cost of one call (one tick or one frame) over the whole ring, about a second, so it
 * is steady enough to read but still follows a module that just got expensive.
 *
 * <p>Recording is a map lookup and two additions and allocates nothing once a module has been seen. Everything
 * here is called from the client thread only. It needs no game classes, so the aggregation is unit-tested.
 */
public final class HookProfiler {
   /** The hooks the event bus times, in display order. */
   public static final int[] HOOKS = {EventBus.TICK, EventBus.RENDER_3D, EventBus.RENDER_HUD};
   public static final int BUCKETS = 4;
   public static final long BUCKET_NANOS = 250_000_000L;
   private final Map<Object, Slot[]> slots = new IdentityHashMap<>();
   private final int buckets;
   private final long bucketNanos;
   private int head;
   private long headStart;
   private boolean started;

   public HookProfiler() {
      this(BUCKETS, BUCKET_NANOS);
   }

   public HookProfiler(int buckets, long bucketNanos) {
      this.buckets = Math.max(1, buckets);
      this.bucketNanos = Math.max(1L, bucketNanos);
   }

   /** One (module, hook) pair's ring. */
   private static final class Slot {
      final long[] nanos;
      final int[] calls;

      Slot(int buckets) {
         this.nanos = new long[buckets];
         this.calls = new int[buckets];
      }
   }

   /** A reusable result of {@link #top}: the heaviest modules of one hook, heaviest first. */
   public static final class Top {
      public final Object[] keys;
      /** Mean microseconds per call. */
      public final float[] micros;
      public int count;
      /** Mean microseconds per call summed over every module on the hook, not just the listed ones. */
      public float totalMicros;

      public Top(int capacity) {
         this.keys = new Object[Math.max(1, capacity)];
         this.micros = new float[Math.max(1, capacity)];
      }
   }

   private static int index(int hook) {
      return hook == EventBus.TICK ? 0 : (hook == EventBus.RENDER_3D ? 1 : (hook == EventBus.RENDER_HUD ? 2 : -1));
   }

   /** Adds one finished hook call. Hooks the profiler does not track are ignored. */
   public void record(Object key, int hook, long nanos) {
      int index = index(hook);
      if (index < 0 || key == null) {
         return;
      }

      Slot[] row = this.slots.get(key);
      if (row == null) {
         row = new Slot[HOOKS.length];
         this.slots.put(key, row);
      }

      Slot slot = row[index];
      if (slot == null) {
         slot = new Slot(this.buckets);
         row[index] = slot;
      }

      slot.nanos[this.head] += Math.max(0L, nanos);
      slot.calls[this.head]++;
   }

   /** Moves the window to {@code nowNanos}; call it regularly (once a tick is plenty). */
   public void advance(long nowNanos) {
      if (!this.started) {
         this.started = true;
         this.headStart = nowNanos;
         return;
      }

      long elapsed = nowNanos - this.headStart;
      if (elapsed < this.bucketNanos) {
         return;
      }

      long steps = elapsed / this.bucketNanos;
      if (steps >= this.buckets) {
         // A long pause: nothing recorded before it is inside the window any more.
         for (Slot[] row : this.slots.values()) {
            for (Slot slot : row) {
               if (slot != null) {
                  java.util.Arrays.fill(slot.nanos, 0L);
                  java.util.Arrays.fill(slot.calls, 0);
               }
            }
         }

         this.head = 0;
         this.headStart = nowNanos;
         return;
      }

      for (long step = 0; step < steps; step++) {
         this.head = (this.head + 1) % this.buckets;
         for (Slot[] row : this.slots.values()) {
            for (Slot slot : row) {
               if (slot != null) {
                  slot.nanos[this.head] = 0L;
                  slot.calls[this.head] = 0;
               }
            }
         }
      }

      this.headStart += steps * this.bucketNanos;
   }

   /** Mean microseconds per call of one module on one hook over the window; 0 when it has not run. */
   public float micros(Object key, int hook) {
      int index = index(hook);
      Slot[] row = index < 0 ? null : this.slots.get(key);
      return row == null ? 0.0F : micros(row[index]);
   }

   private static float micros(Slot slot) {
      if (slot == null) {
         return 0.0F;
      }

      long nanos = 0L;
      int calls = 0;
      for (int i = 0; i < slot.nanos.length; i++) {
         nanos += slot.nanos[i];
         calls += slot.calls[i];
      }

      return calls == 0 ? 0.0F : nanos / (float)calls / 1000.0F;
   }

   /** Fills {@code out} with the heaviest modules of the hook, heaviest first, up to its capacity. */
   public void top(int hook, Top out) {
      out.count = 0;
      out.totalMicros = 0.0F;
      int index = index(hook);
      if (index < 0) {
         return;
      }

      for (Map.Entry<Object, Slot[]> entry : this.slots.entrySet()) {
         float cost = micros(entry.getValue()[index]);
         if (cost <= 0.0F) {
            continue;
         }

         out.totalMicros += cost;
         int at = out.count;
         if (at == out.keys.length) {
            if (cost <= out.micros[at - 1]) {
               continue;
            }

            at--;
         } else {
            out.count++;
         }

         // Insertion into the (short) sorted result.
         while (at > 0 && out.micros[at - 1] < cost) {
            out.keys[at] = out.keys[at - 1];
            out.micros[at] = out.micros[at - 1];
            at--;
         }

         out.keys[at] = entry.getKey();
         out.micros[at] = cost;
      }
   }

   /** Drops every timing; the next {@link #advance} starts a fresh window. */
   public void reset() {
      this.slots.clear();
      this.head = 0;
      this.started = false;
   }
}
