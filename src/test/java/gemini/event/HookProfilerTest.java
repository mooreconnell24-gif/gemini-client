package gemini.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import gemini.module.Category;
import gemini.module.Module;
import org.junit.jupiter.api.Test;

class HookProfilerTest {
   private static final long BUCKET = 250_000_000L;
   private final Object heavy = new Object();
   private final Object light = new Object();

   @Test
   void reportsMeanMicrosecondsPerCall() {
      HookProfiler profiler = new HookProfiler(4, BUCKET);
      profiler.advance(0L);
      profiler.record(this.heavy, EventBus.TICK, 3_000_000L);
      profiler.record(this.heavy, EventBus.TICK, 1_000_000L);
      profiler.record(this.light, EventBus.TICK, 20_000L);
      assertEquals(2000.0F, profiler.micros(this.heavy, EventBus.TICK), 0.01F);
      assertEquals(20.0F, profiler.micros(this.light, EventBus.TICK), 0.01F);
      assertEquals(0.0F, profiler.micros(this.heavy, EventBus.RENDER_HUD), "hooks are kept apart");
   }

   @Test
   void oldSamplesRollOutOfTheWindow() {
      HookProfiler profiler = new HookProfiler(4, BUCKET);
      profiler.advance(0L);
      profiler.record(this.heavy, EventBus.RENDER_3D, 8_000_000L);
      profiler.advance(BUCKET);
      profiler.record(this.heavy, EventBus.RENDER_3D, 2_000_000L);
      assertEquals(5000.0F, profiler.micros(this.heavy, EventBus.RENDER_3D), 0.01F, "both buckets are inside the window");
      profiler.advance(4 * BUCKET);
      assertEquals(2000.0F, profiler.micros(this.heavy, EventBus.RENDER_3D), 0.01F, "the first bucket fell out after a second");
      profiler.advance(5 * BUCKET);
      assertEquals(0.0F, profiler.micros(this.heavy, EventBus.RENDER_3D));
   }

   @Test
   void aLongPauseClearsEverything() {
      HookProfiler profiler = new HookProfiler(4, BUCKET);
      profiler.advance(0L);
      profiler.record(this.heavy, EventBus.TICK, 1_000_000L);
      profiler.advance(60L * 1_000_000_000L);
      assertEquals(0.0F, profiler.micros(this.heavy, EventBus.TICK));
      profiler.record(this.heavy, EventBus.TICK, 4_000L);
      assertEquals(4.0F, profiler.micros(this.heavy, EventBus.TICK), 0.01F);
   }

   @Test
   void topListsTheHeaviestFirstAndTotalsEveryone() {
      HookProfiler profiler = new HookProfiler(4, BUCKET);
      profiler.advance(0L);
      Object[] keys = new Object[6];
      for (int i = 0; i < keys.length; i++) {
         keys[i] = new Object();
         profiler.record(keys[i], EventBus.RENDER_HUD, (i + 1) * 1_000L);
      }

      HookProfiler.Top top = new HookProfiler.Top(3);
      profiler.top(EventBus.RENDER_HUD, top);
      assertEquals(3, top.count);
      assertSame(keys[5], top.keys[0]);
      assertSame(keys[4], top.keys[1]);
      assertSame(keys[3], top.keys[2]);
      assertEquals(21.0F, top.totalMicros, 0.01F);
      profiler.top(EventBus.SEND_PACKET, top);
      assertEquals(0, top.count, "untracked hooks report nothing");
   }

   @Test
   void theBusDoesNotTimeAnythingWhileProfilingIsOff() {
      List<Integer> hooks = new ArrayList<>();
      boolean[] on = {false};
      EventBus bus = new EventBus((module, hook, error) -> {
      }, new EventBus.Profiler() {
         @Override
         public boolean profiling() {
            return on[0];
         }

         @Override
         public void record(Module module, int hook, long nanos) {
            hooks.add(hook);
         }
      });
      Module probe = new Module("probe", "test", Category.CLIENT) {
         @Override
         protected boolean settingsOnly() {
            return true;
         }

         @Override
         public void onTick() {
         }

         @Override
         public void onRender3D(float partialTick) {
         }
      };
      probe.loadState(true, -1);
      bus.register(probe);
      bus.tick();
      bus.render3D(0.0F);
      assertEquals(List.of(), hooks);
      on[0] = true;
      bus.tick();
      bus.render3D(0.0F);
      assertEquals(List.of(EventBus.TICK, EventBus.RENDER_3D), hooks);
   }
}
