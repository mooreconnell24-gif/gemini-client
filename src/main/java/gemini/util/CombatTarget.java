package gemini.util;

import gemini.module.Module;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;

/**
 * The entity a combat module is attacking right now, so the Target HUD shows the same one the module has chosen.
 *
 * <p>An attacking module calls {@link #report} every tick it holds a target and {@link #release} when it lets go
 * (on disable and world change). A report goes stale after {@link #TTL_MS} without a refresh, so a module that
 * simply stops reporting never leaves a stale target behind. The most recent report wins.
 */
public final class CombatTarget {
   /** How long a report stays current without being refreshed: a little over five ticks. */
   static final long TTL_MS = 300L;
   private static final Slot<Module, LivingEntity> SLOT = new Slot<>(TTL_MS);

   private CombatTarget() {
   }

   /** {@code owner} is attacking {@code target} this tick; null means it has no target (same as {@link #release}). */
   public static void report(Module owner, LivingEntity target) {
      if (target == null) {
         SLOT.release(owner);
      } else {
         SLOT.report(owner, target, Util.getMillis());
      }
   }

   /** {@code owner} stopped attacking: forget its target if it is the one shown. */
   public static void release(Module owner) {
      SLOT.release(owner);
   }

   /** The target an enabled module reported in the last few ticks, if it is still alive, else null. */
   public static LivingEntity current() {
      LivingEntity target = SLOT.get(Util.getMillis());
      Module owner = SLOT.owner();
      if (target == null || owner == null || !owner.isEnabled() || !target.isAlive() || target.isRemoved()) {
         return null;
      }

      return target;
   }

   /** Most-recent-wins holder with an expiry; pure so it can be tested. */
   static final class Slot<O, T> {
      private final long ttlMs;
      private O owner;
      private T value;
      private long reportedAt;

      Slot(long ttlMs) {
         this.ttlMs = ttlMs;
      }

      void report(O owner, T value, long nowMs) {
         this.owner = owner;
         this.value = value;
         this.reportedAt = nowMs;
      }

      /** Clears the slot only if {@code owner} made the current report, so one module cannot wipe another's. */
      void release(O owner) {
         if (this.owner == owner) {
            this.owner = null;
            this.value = null;
         }
      }

      T get(long nowMs) {
         return this.value != null && nowMs - this.reportedAt <= this.ttlMs ? this.value : null;
      }

      O owner() {
         return this.owner;
      }
   }
}
