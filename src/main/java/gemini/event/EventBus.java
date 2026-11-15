package gemini.event;

import gemini.module.Module;
import gemini.ui.HudContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dispatches game events to modules in priority order.
 *
 * <p><b>Dispatch.</b> Each hook ({@link Module#onTick()}, {@link Module#onRender3D(float)}, ...) has its own
 * immutable array of {@link Listener}s: the enabled modules that override the hook, sorted by
 * {@link ActionPriority} (highest first, registration order within a priority), each paired with the priority it
 * runs at. An event is one loop over that array calling the hook directly on the module. Nothing is looked up,
 * allocated or reflected per event.
 *
 * <p><b>Registration.</b> Which hooks a module class overrides, and any {@link EventHandler} priorities on them, is
 * worked out once per class and cached in a {@link ClassValue}, so a second instance of a class costs nothing.
 *
 * <p><b>Toggles while an event is running.</b> The listener arrays are snapshots that are replaced, never edited.
 * A module switched off part-way through an event is skipped by the {@code isEnabled()} check; one switched on
 * joins from the next event. Toggling therefore can never corrupt a loop that is in flight, whether the toggle
 * comes from another module, a key bind, the menu or a crash handler. Invalidation is a counter bump, so it is
 * safe from any thread.
 *
 * <p><b>Who is running.</b> {@link #current()} and {@link #currentPriority()} name the module whose hook is running
 * on the client thread, which is how the hand and rotation arbiters know whose request they are looking at. The
 * context is saved and restored around every hook, so an event raised from inside a hook (a tick handler that
 * sends a packet, an attack raised from a tick) hands the context back to the outer module when it returns.
 */
public final class EventBus {
   public static final int TICK = 0;
   public static final int RENDER_3D = 1;
   public static final int RENDER_HUD = 2;
   public static final int SEND_PACKET = 3;
   public static final int RECEIVE_PACKET = 4;
   public static final int ATTACK = 5;
   public static final int BLOCK_UPDATE = 6;
   public static final int BARITONE_PAUSE = 7;
   static final int HOOKS = 8;
   private static final String[] HOOK_NAMES = {
      "onTick", "onRender3D", "onRenderHud", "onSendPacket", "onReceivePacket", "onAttack", "onBlockUpdate", "wantsBaritonePause"
   };
   private static final Class<?>[][] HOOK_PARAMS = {
      {}, {float.class}, {HudContext.class}, {Packet.class}, {Packet.class}, {Entity.class}, {BlockPos.class, BlockState.class, BlockState.class}, {}
   };
   private static final Listener[] NONE = new Listener[0];
   /** Bumped whenever any module turns on or off; every bus compares it with the generation its tables were built at. */
   private static final AtomicInteger GENERATION = new AtomicInteger();
   private static final ClassValue<HookProfile> PROFILES = new ClassValue<>() {
      @Override
      protected HookProfile computeValue(Class<?> type) {
         return HookProfile.scan(type);
      }
   };
   private static Module current;
   private static ActionPriority currentPriority;
   private final Object registrationLock = new Object();
   private final Failure failure;
   private final Profiler profiler;
   /** Every registered module, replaced (not edited) on registration so rebuilds can read it without locking. */
   private volatile Subscription[] subscriptions = new Subscription[0];
   private volatile Listener[][] listeners = new Listener[HOOKS][];
   private volatile int builtGeneration = -1;

   public EventBus(Failure failure, Profiler profiler) {
      this.failure = failure;
      this.profiler = profiler;
      Arrays.fill(this.listeners, NONE);
   }

   /** Adds a module. Its hooks start receiving events once it is enabled. */
   public void register(Module module) {
      synchronized (this.registrationLock) {
         Subscription[] old = this.subscriptions;
         Subscription[] next = Arrays.copyOf(old, old.length + 1);
         next[old.length] = new Subscription(module, PROFILES.get(module.getClass()), old.length);
         this.subscriptions = next;
      }

      invalidate();
   }

   /** Marks the listener tables stale; called whenever a module turns on or off. Safe from any thread. */
   public static void invalidate() {
      GENERATION.incrementAndGet();
   }

   /** The module whose hook is running on the client thread right now, or null outside dispatch. */
   public static Module current() {
      return current;
   }

   /** The effective priority of the hook running right now (module priority unless the hook overrides it). */
   public static ActionPriority currentPriority() {
      return currentPriority;
   }

   /** True when the module overrides the given hook, whether or not it is enabled. */
   public boolean subscribed(Module module, int hook) {
      for (Subscription subscription : this.subscriptions) {
         if (subscription.module == module) {
            return subscription.profile.overrides(hook);
         }
      }

      return false;
   }

   /** How many enabled modules currently listen to the hook. */
   public int listenerCount(int hook) {
      return this.listeners(hook).length;
   }

   private Listener[] listeners(int hook) {
      if (this.builtGeneration != GENERATION.get()) {
         this.rebuild();
      }

      return this.listeners[hook];
   }

   private void rebuild() {
      synchronized (this.registrationLock) {
         int generation = GENERATION.get();
         if (this.builtGeneration == generation) {
            return;
         }

         Subscription[] all = this.subscriptions;
         Listener[][] next = new Listener[HOOKS][];
         List<Listener> enabled = new ArrayList<>();
         for (int hook = 0; hook < HOOKS; hook++) {
            enabled.clear();
            for (Subscription subscription : all) {
               if (subscription.profile.overrides(hook) && subscription.module.isEnabled()) {
                  enabled.add(new Listener(subscription.module, subscription.priority(hook), subscription.order));
               }
            }

            enabled.sort(Listener.ORDER);
            next[hook] = enabled.isEmpty() ? NONE : enabled.toArray(new Listener[0]);
         }

         this.listeners = next;
         // Published last: a module toggled while this ran bumped the counter again and forces another rebuild.
         this.builtGeneration = generation;
      }
   }

   public void tick() {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Listener listener : this.listeners(TICK)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               module.onTick();
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onTick", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public void render3D(float partialTick) {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Listener listener : this.listeners(RENDER_3D)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               module.onRender3D(partialTick);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onRender3D", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public void renderHud(HudContext hud) {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Listener listener : this.listeners(RENDER_HUD)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               module.onRenderHud(hud);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onRenderHud", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   /** Returns true when a module cancelled the packet. Every listener still sees it. */
   public boolean sendPacket(Packet<?> packet) {
      boolean cancelled = false;
      for (Listener listener : this.listeners(SEND_PACKET)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               cancelled |= module.onSendPacket(packet);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onSendPacket", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }
         }
      }

      return cancelled;
   }

   /** Packets may arrive off the client thread, so this one does not publish {@link #current()}. */
   public boolean receivePacket(Packet<?> packet) {
      boolean cancelled = false;
      for (Listener listener : this.listeners(RECEIVE_PACKET)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            try {
               cancelled |= module.onReceivePacket(packet);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onReceivePacket", error);
            }
         }
      }

      return cancelled;
   }

   public void attack(Entity target) {
      for (Listener listener : this.listeners(ATTACK)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               module.onAttack(target);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onAttack", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }
         }
      }
   }

   public void blockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
      for (Listener listener : this.listeners(BLOCK_UPDATE)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            Module outer = current;
            ActionPriority outerPriority = currentPriority;
            current = module;
            currentPriority = listener.priority;

            try {
               module.onBlockUpdate(pos, oldState, newState);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onBlockUpdate", error);
            } finally {
               current = outer;
               currentPriority = outerPriority;
            }
         }
      }
   }

   public boolean wantsBaritonePause() {
      for (Listener listener : this.listeners(BARITONE_PAUSE)) {
         Module module = listener.module;
         if (module.isEnabled()) {
            try {
               if (module.wantsBaritonePause()) {
                  return true;
               }
            } catch (RuntimeException | LinkageError error) {
               this.failure.failed(module, "wantsBaritonePause", error);
            }
         }
      }

      return false;
   }

   /** Receives hook failures; the module manager disables and reports the module. */
   @FunctionalInterface
   public interface Failure {
      void failed(Module module, String hook, Throwable error);
   }

   /** Optional per-module timing, used by the self test. */
   public interface Profiler {
      boolean profiling();

      void record(Module module, long nanos);
   }

   /** One enabled module on one hook, with the priority resolved up front. */
   private static final class Listener {
      static final Comparator<Listener> ORDER = (a, b) -> {
         int byPriority = Integer.compare(b.priority.level(), a.priority.level());
         return byPriority != 0 ? byPriority : Integer.compare(a.order, b.order);
      };
      final Module module;
      final ActionPriority priority;
      final int order;

      Listener(Module module, ActionPriority priority, int order) {
         this.module = module;
         this.priority = priority;
         this.order = order;
      }
   }

   private static final class Subscription {
      final Module module;
      final HookProfile profile;
      final int order;

      Subscription(Module module, HookProfile profile, int order) {
         this.module = module;
         this.profile = profile;
         this.order = order;
      }

      ActionPriority priority(int hook) {
         ActionPriority level = this.profile.levels[hook];
         return level != null ? level : this.module.priority();
      }
   }

   /** Which hooks a module class overrides and the {@link EventHandler} priority on each; computed once per class. */
   private static final class HookProfile {
      final int mask;
      final ActionPriority[] levels;

      private HookProfile(int mask, ActionPriority[] levels) {
         this.mask = mask;
         this.levels = levels;
      }

      boolean overrides(int hook) {
         return (this.mask & 1 << hook) != 0;
      }

      static HookProfile scan(Class<?> type) {
         int mask = 0;
         ActionPriority[] levels = new ActionPriority[HOOKS];
         // Walk from the concrete class up to (not including) Module: the first class that declares a hook is the
         // override that will run, so its annotation is the one that counts.
         for (Class<?> c = type; c != null && c != Module.class && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
               if (method.isBridge() || method.isSynthetic()) {
                  continue;
               }

               int hook = hookOf(method);
               if (hook >= 0 && (mask & 1 << hook) == 0) {
                  mask |= 1 << hook;
                  EventHandler handler = method.getAnnotation(EventHandler.class);
                  levels[hook] = handler != null ? handler.value() : null;
               }
            }
         }

         return new HookProfile(mask, levels);
      }

      private static int hookOf(Method method) {
         String name = method.getName();
         for (int hook = 0; hook < HOOKS; hook++) {
            if (HOOK_NAMES[hook].equals(name) && Arrays.equals(HOOK_PARAMS[hook], method.getParameterTypes())) {
               return hook;
            }
         }

         return -1;
      }
   }
}
