package gemini.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.module.Category;
import gemini.module.Module;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import org.junit.jupiter.api.Test;

class EventBusTest {
   /** A module that needs no game: it is "settings only", so it may be enabled without a world. */
   private static class Probe extends Module {
      final List<String> log;
      final ActionPriority priority;
      Runnable duringTick;

      Probe(String name, ActionPriority priority, List<String> log) {
         super(name, "test", Category.CLIENT);
         this.priority = priority;
         this.log = log;
      }

      @Override
      protected boolean settingsOnly() {
         return true;
      }

      @Override
      public ActionPriority priority() {
         return this.priority;
      }

      @Override
      public void onTick() {
         this.log.add(this.getName());
         if (this.duringTick != null) {
            this.duringTick.run();
         }
      }

      Probe on() {
         this.loadState(true, -1);
         return this;
      }

      /** Module.toggle() also posts a chat notification, which needs the game; this is the same switch without it. */
      void toggleQuietly() {
         this.setEnabled(!this.isSavedEnabled(), false);
      }
   }

   private static final class PacketProbe extends Probe {
      PacketProbe(String name, List<String> log) {
         super(name, ActionPriority.COMBAT, log);
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         this.log.add(this.getName() + ":packet as " + EventBus.current().getName() + "/" + EventBus.currentPriority());
         return false;
      }
   }

   private static final class LateTick extends Probe {
      LateTick(String name, List<String> log) {
         super(name, ActionPriority.CRITICAL_SURVIVAL, log);
      }

      @Override
      @EventHandler(ActionPriority.BOT)
      public void onTick() {
         super.onTick();
      }
   }

   private final List<String> log = new ArrayList<>();
   private final List<String> failures = new ArrayList<>();
   private final EventBus bus = new EventBus((module, hook, error) -> this.failures.add(module.getName() + "." + hook), null);

   private <T extends Module> T add(T module) {
      this.bus.register(module);
      return module;
   }

   @Test
   void higherPrioritiesRunFirstAndRegistrationOrderBreaksTies() {
      this.add(new Probe("world-1", ActionPriority.WORLD_UTILITY, this.log).on());
      this.add(new Probe("combat", ActionPriority.COMBAT, this.log).on());
      this.add(new Probe("world-2", ActionPriority.WORLD_UTILITY, this.log).on());
      this.add(new Probe("survival", ActionPriority.CRITICAL_SURVIVAL, this.log).on());
      this.add(new Probe("bot", ActionPriority.BOT, this.log).on());
      this.bus.tick();
      assertEquals(List.of("survival", "combat", "world-1", "world-2", "bot"), this.log);
   }

   @Test
   void anEventHandlerPriorityOverridesTheModulePriorityForThatHook() {
      this.add(new LateTick("late", this.log).on());
      this.add(new Probe("world", ActionPriority.WORLD_UTILITY, this.log).on());
      this.bus.tick();
      assertEquals(List.of("world", "late"), this.log);
   }

   @Test
   void onlyEnabledModulesThatOverrideAHookListenToIt() {
      Probe off = this.add(new Probe("off", ActionPriority.COMBAT, this.log));
      Probe on = this.add(new Probe("on", ActionPriority.COMBAT, this.log).on());
      assertEquals(1, this.bus.listenerCount(EventBus.TICK));
      assertEquals(0, this.bus.listenerCount(EventBus.RENDER_3D));
      assertTrue(this.bus.subscribed(off, EventBus.TICK));
      assertFalse(this.bus.subscribed(on, EventBus.SEND_PACKET));
      this.bus.tick();
      assertEquals(List.of("on"), this.log);
      off.on();
      this.log.clear();
      this.bus.tick();
      assertEquals(List.of("off", "on"), this.log);
   }

   @Test
   void aModuleSwitchedOffDuringAnEventIsSkippedInThatEvent() {
      Probe first = this.add(new Probe("first", ActionPriority.COMBAT, this.log).on());
      Probe second = this.add(new Probe("second", ActionPriority.WORLD_UTILITY, this.log).on());
      first.duringTick = () -> second.setEnabled(false, false);
      this.bus.tick();
      assertEquals(List.of("first"), this.log);
   }

   @Test
   void aModuleSwitchedOnDuringAnEventJoinsFromTheNextOne() {
      Probe first = this.add(new Probe("first", ActionPriority.COMBAT, this.log).on());
      Probe late = this.add(new Probe("late", ActionPriority.CRITICAL_SURVIVAL, this.log));
      first.duringTick = () -> late.setEnabled(true, false);
      this.bus.tick();
      assertEquals(List.of("first"), this.log, "the running event keeps the listeners it started with");
      first.duringTick = null;
      this.log.clear();
      this.bus.tick();
      assertEquals(List.of("late", "first"), this.log);
   }

   @Test
   void togglingManyModulesFromInsideAnEventNeverBreaksTheLoop() {
      List<Probe> probes = new ArrayList<>();
      for (int i = 0; i < 50; i++) {
         probes.add(this.add(new Probe("m" + i, ActionPriority.WORLD_UTILITY, this.log).on()));
      }

      for (int i = 0; i < 50; i++) {
         int index = i;
         probes.get(i).duringTick = () -> {
            probes.get((index + 7) % 50).toggleQuietly();
            probes.get((index + 13) % 50).toggleQuietly();
         };
      }

      for (int round = 0; round < 200; round++) {
         this.bus.tick();
      }

      assertTrue(this.failures.isEmpty(), "toggling during dispatch must never throw: " + this.failures);
   }

   @Test
   void theRunningModuleIsRestoredAfterAnEventRaisedFromInsideAHook() {
      Probe outer = this.add(new Probe("outer", ActionPriority.CRITICAL_SURVIVAL, this.log).on());
      this.add(new PacketProbe("inner", this.log).on());
      outer.duringTick = () -> {
         this.bus.sendPacket(null);
         this.log.add("after: " + EventBus.current().getName() + "/" + EventBus.currentPriority());
      };
      this.bus.tick();
      assertEquals(List.of("outer", "inner:packet as inner/COMBAT", "after: outer/CRITICAL_SURVIVAL", "inner"), this.log);
      assertNull(EventBus.current(), "nothing is running once the event is over");
      assertNull(EventBus.currentPriority());
   }

   @Test
   void aFailingHookIsReportedAndTheOthersStillRun() {
      Probe broken = this.add(new Probe("broken", ActionPriority.COMBAT, this.log).on());
      this.add(new Probe("fine", ActionPriority.WORLD_UTILITY, this.log).on());
      broken.duringTick = () -> {
         throw new IllegalStateException("boom");
      };
      this.bus.tick();
      assertEquals(List.of("broken", "fine"), this.log);
      assertEquals(List.of("broken.onTick"), this.failures);
      assertNull(EventBus.current(), "a failure must not leave a stale running module behind");
   }

   @Test
   void theProfilerSeesEveryTimedHook() {
      List<Module> timed = new ArrayList<>();
      EventBus profiled = new EventBus((module, hook, error) -> {
      }, new EventBus.Profiler() {
         @Override
         public boolean profiling() {
            return true;
         }

         @Override
         public void record(Module module, long nanos) {
            timed.add(module);
         }
      });
      Probe probe = new Probe("timed", ActionPriority.COMBAT, this.log).on();
      profiled.register(probe);
      profiled.tick();
      assertEquals(1, timed.size());
      assertSame(probe, timed.get(0));
   }
}
