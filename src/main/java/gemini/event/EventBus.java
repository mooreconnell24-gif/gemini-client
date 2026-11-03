package gemini.event;

import gemini.module.Module;
import gemini.ui.HudContext;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dispatches game events to modules in priority order.
 *
 * <p>Each hook ({@link Module#onTick()}, {@link Module#onRender3D(float)}, ...) has its own subscriber array holding
 * only the enabled modules that override it, sorted by {@link ActionPriority} (highest first, registration order
 * within a priority). The arrays are rebuilt only when a module is registered, enabled or disabled, so every event
 * is a plain loop with no allocation, lookup or reflection. Per-hook priorities come from {@link EventHandler}.
 *
 * <p>The module currently being dispatched on the client thread is available through {@link #current()}, which is
 * how the hand and rotation arbiters know whose request they are looking at.
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
   private static final String[] HOOK_NAMES = {"onTick", "onRender3D", "onRenderHud", "onSendPacket", "onReceivePacket", "onAttack", "onBlockUpdate", "wantsBaritonePause"};
   private static final Class<?>[][] HOOK_PARAMS = {
      {}, {float.class}, {HudContext.class}, {Packet.class}, {Packet.class}, {Entity.class}, {BlockPos.class, BlockState.class, BlockState.class}, {}
   };
   private static final Module[] NONE = new Module[0];
   private static volatile boolean dirty = true;
   private static Module current;
   private static ActionPriority currentPriority;
   private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
   private final Failure failure;
   private final Profiler profiler;
   private volatile Module[][] subscribers = new Module[HOOKS][];

   public EventBus(Failure failure, Profiler profiler) {
      this.failure = failure;
      this.profiler = profiler;
      Arrays.fill(this.subscribers, NONE);
   }

   /** Reads which hooks the module overrides and at what priority. Called once per module. */
   public void register(Module module) {
      int mask = 0;
      ActionPriority[] levels = new ActionPriority[HOOKS];
      for (int hook = 0; hook < HOOKS; hook++) {
         try {
            Method method = module.getClass().getMethod(HOOK_NAMES[hook], HOOK_PARAMS[hook]);
            if (method.getDeclaringClass() != Module.class) {
               mask |= 1 << hook;
               EventHandler handler = method.getAnnotation(EventHandler.class);
               levels[hook] = handler != null ? handler.value() : null;
            }
         } catch (NoSuchMethodException ignored) {
         }
      }

      this.subscriptions.add(new EventBus.Subscription(module, mask, levels, this.subscriptions.size()));
      dirty = true;
   }

   /** Marks the subscriber arrays stale; called whenever a module turns on or off. */
   public static void invalidate() {
      dirty = true;
   }

   /** The module whose hook is running on the client thread right now, or null outside dispatch. */
   public static Module current() {
      return current;
   }

   /** The effective priority of the hook running right now (module priority unless the hook overrides it). */
   public static ActionPriority currentPriority() {
      return currentPriority;
   }

   public boolean subscribed(Module module, int hook) {
      for (EventBus.Subscription subscription : this.subscriptions) {
         if (subscription.module == module) {
            return (subscription.mask & 1 << hook) != 0;
         }
      }

      return false;
   }

   private Module[] subscribers(int hook) {
      if (dirty) {
         this.rebuild();
      }

      return this.subscribers[hook];
   }

   private synchronized void rebuild() {
      if (!dirty) {
         return;
      }

      dirty = false;
      Module[][] next = new Module[HOOKS][];
      List<EventBus.Subscription> enabled = new ArrayList<>();
      for (int hook = 0; hook < HOOKS; hook++) {
         enabled.clear();
         for (EventBus.Subscription subscription : this.subscriptions) {
            if ((subscription.mask & 1 << hook) != 0 && subscription.module.isEnabled()) {
               enabled.add(subscription);
            }
         }

         int h = hook;
         enabled.sort(Comparator.<EventBus.Subscription>comparingInt(s -> -s.priority(h).level()).thenComparingInt(s -> s.order));
         Module[] array = new Module[enabled.size()];
         for (int i = 0; i < array.length; i++) {
            array[i] = enabled.get(i).module;
         }

         next[hook] = array;
      }

      this.subscribers = next;
   }

   private ActionPriority priorityOf(Module module, int hook) {
      for (EventBus.Subscription subscription : this.subscriptions) {
         if (subscription.module == module) {
            return subscription.priority(hook);
         }
      }

      return module.priority();
   }

   private void enter(Module module, int hook) {
      current = module;
      currentPriority = this.priorityOf(module, hook);
   }

   private static void leave() {
      current = null;
      currentPriority = null;
   }

   public void tick() {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Module module : this.subscribers(TICK)) {
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            this.enter(module, TICK);

            try {
               module.onTick();
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onTick", error);
            } finally {
               leave();
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public void render3D(float partialTick) {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Module module : this.subscribers(RENDER_3D)) {
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            this.enter(module, RENDER_3D);

            try {
               module.onRender3D(partialTick);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onRender3D", error);
            } finally {
               leave();
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   public void renderHud(HudContext hud) {
      boolean timed = this.profiler != null && this.profiler.profiling();
      for (Module module : this.subscribers(RENDER_HUD)) {
         if (module.isEnabled()) {
            long start = timed ? System.nanoTime() : 0L;
            this.enter(module, RENDER_HUD);

            try {
               module.onRenderHud(hud);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onRenderHud", error);
            } finally {
               leave();
            }

            if (timed) {
               this.profiler.record(module, System.nanoTime() - start);
            }
         }
      }
   }

   /** Returns true when a module cancelled the packet. */
   public boolean sendPacket(Packet<?> packet) {
      boolean cancelled = false;
      for (Module module : this.subscribers(SEND_PACKET)) {
         if (module.isEnabled()) {
            this.enter(module, SEND_PACKET);

            try {
               cancelled |= module.onSendPacket(packet);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onSendPacket", error);
            } finally {
               leave();
            }
         }
      }

      return cancelled;
   }

   /** Packets may arrive off the client thread, so this one does not publish {@link #current()}. */
   public boolean receivePacket(Packet<?> packet) {
      boolean cancelled = false;
      for (Module module : this.subscribers(RECEIVE_PACKET)) {
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
      for (Module module : this.subscribers(ATTACK)) {
         if (module.isEnabled()) {
            this.enter(module, ATTACK);

            try {
               module.onAttack(target);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onAttack", error);
            } finally {
               leave();
            }
         }
      }
   }

   public void blockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
      for (Module module : this.subscribers(BLOCK_UPDATE)) {
         if (module.isEnabled()) {
            this.enter(module, BLOCK_UPDATE);

            try {
               module.onBlockUpdate(pos, oldState, newState);
            } catch (StackOverflowError | RuntimeException | LinkageError error) {
               this.failure.failed(module, "onBlockUpdate", error);
            } finally {
               leave();
            }
         }
      }
   }

   public boolean wantsBaritonePause() {
      for (Module module : this.subscribers(BARITONE_PAUSE)) {
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

   private static final class Subscription {
      final Module module;
      final int mask;
      final ActionPriority[] levels;
      final int order;

      Subscription(Module module, int mask, ActionPriority[] levels, int order) {
         this.module = module;
         this.mask = mask;
         this.levels = levels;
         this.order = order;
      }

      ActionPriority priority(int hook) {
         ActionPriority level = this.levels[hook];
         return level != null ? level : this.module.priority();
      }
   }
}
