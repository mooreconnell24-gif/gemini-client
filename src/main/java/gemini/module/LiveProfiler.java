package gemini.module;

import gemini.event.EventBus;
import gemini.event.HookProfiler;
import gemini.module.modules.hud.AntiLagHud;
import gemini.module.modules.misc.Performance;
import java.util.List;
import java.util.Locale;

/**
 * The live module profiler: switches the event bus's hook timing on while a HUD wants it and keeps the rolling
 * figures in a {@link HookProfiler}.
 *
 * <p>The gate is {@link #active()}, re-evaluated once a tick from the two toggles that can ask for it (the
 * Performance module's "Profiler" switch and the AntiLag Status widget's "Module Profiler" switch). While it is
 * false the bus never reads the clock and nothing is recorded.
 */
public final class LiveProfiler {
   private static final HookProfiler DATA = new HookProfiler();
   private static final HookProfiler.Top TOP = new HookProfiler.Top(12);
   private static final String[] HOOK_LABELS = {"Tick", "World frame", "HUD frame"};
   private static boolean active;
   private static Performance performance;
   private static AntiLagHud antiLagHud;

   private LiveProfiler() {
   }

   /** True while some HUD is showing the profiler; read by the event bus before it times anything. */
   public static boolean active() {
      return active;
   }

   static void record(Module module, int hook, long nanos) {
      if (active) {
         DATA.record(module, hook, nanos);
      }
   }

   /** Once per client tick: works out whether anyone wants the profiler and moves the rolling window on. */
   static void update(ModuleManager manager) {
      if (performance == null) {
         performance = manager.byClass(Performance.class).orElse(null);
         antiLagHud = manager.byClass(AntiLagHud.class).orElse(null);
      }

      boolean wanted = performance != null && performance.wantsProfiler() || antiLagHud != null && antiLagHud.wantsProfiler();
      if (wanted != active) {
         DATA.reset();
         active = wanted;
      }

      if (active) {
         DATA.advance(System.nanoTime());
      }
   }

   /** Stops profiling and forgets the figures (leaving the world, switching Gemini off). */
   static void stop() {
      if (active) {
         active = false;
         DATA.reset();
      }
   }

   /** Name and cost of the heaviest module on the tick hook, or null when nothing has been measured. */
   public static String heaviest() {
      if (!active) {
         return null;
      }

      DATA.top(EventBus.TICK, TOP);
      return TOP.count == 0 ? null : ((Module)TOP.keys[0]).getName() + " " + micros(TOP.micros[0]);
   }

   /**
    * Appends the breakdown as text lines: per hook a heading with the hook's total and then its {@code rows}
    * heaviest modules, in mean microseconds per tick or frame over the last second. {@code hookFilter} is "All" or
    * one of "Tick", "World frame", "HUD frame". Meant to be called a few times a second, not every frame.
    */
   public static void describe(List<TextHudWidget.Line> out, int rows, String hookFilter, int accent, int text, int dim) {
      if (!active) {
         out.add(new TextHudWidget.Line("Profiler starting...", dim));
         return;
      }

      int limit = Math.max(1, Math.min(TOP.keys.length, rows));
      for (int i = 0; i < HookProfiler.HOOKS.length; i++) {
         if (!"All".equals(hookFilter) && !HOOK_LABELS[i].equals(hookFilter)) {
            continue;
         }

         DATA.top(HookProfiler.HOOKS[i], TOP);
         out.add(new TextHudWidget.Line(HOOK_LABELS[i] + "  " + micros(TOP.totalMicros) + " total", accent));
         if (TOP.count == 0) {
            out.add(new TextHudWidget.Line("  nothing running", dim));
         }

         for (int row = 0; row < Math.min(limit, TOP.count); row++) {
            out.add(new TextHudWidget.Line("  " + ((Module)TOP.keys[row]).getName() + "  " + micros(TOP.micros[row]), row == 0 ? text : dim));
         }
      }
   }

   private static String micros(float value) {
      return value >= 1000.0F ? String.format(Locale.ROOT, "%.2f ms", value / 1000.0F) : String.format(Locale.ROOT, "%.0f µs", value);
   }
}
