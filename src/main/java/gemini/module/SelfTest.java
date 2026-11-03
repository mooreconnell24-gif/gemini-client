package gemini.module;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.Setting;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

public final class SelfTest {
   private static final int DURATION_TICKS = 140;
   private static final int WARMUP_TICKS = 20;
   private static final int MAX_CHAT_LINES = 8;
   private static final int HEAVIEST = 5;
   private static SelfTest current;
   private final Map<Module, Boolean> before = new LinkedHashMap<>();
   private final List<Module> started = new ArrayList<>();
   private final List<String> refused = new ArrayList<>();
   private final List<String> problems = new ArrayList<>();
   private final Map<Module, long[]> cost = new IdentityHashMap<>();
   private int ticks;
   private int skipped;
   private long measureStart;

   private SelfTest() {
   }

   public static boolean running() {
      return current != null;
   }

   public static boolean profiling() {
      SelfTest test = current;
      return test != null && test.ticks >= 20;
   }

   public static void record(Module module, long nanos) {
      SelfTest test = current;
      if (test != null) {
         test.cost.computeIfAbsent(module, key -> new long[1])[0] += nanos;
      }
   }

   public static void start(ModuleManager manager) {
      Minecraft client = Minecraft.getInstance();
      if (current != null) {
         CommandManager.feedback("§cThe self-test is already running.");
      } else if (client.player != null && SingleplayerGate.singleplayerOnly()) {
         Gemini.getInstance().config().setSavingPaused(true);
         ModuleManager.drainFailures();
         SelfTest test = new SelfTest();
         current = test;
         test.begin(manager);
      } else {
         CommandManager.feedback("§cThe self-test turns everything on at once, so it only runs in a single-player world.");
      }
   }

   static void tick(ModuleManager manager) {
      SelfTest test = current;
      if (test != null) {
         test.ticks++;
         if (test.ticks == 20) {
            test.measureStart = System.nanoTime();
         }

         if (test.ticks >= 140) {
            test.finish(manager, null);
         }
      }
   }

   static void abort(String reason) {
      SelfTest test = current;
      if (test != null) {
         test.finish(Gemini.getInstance().modules(), reason);
      }
   }

   private void begin(ModuleManager manager) {
      for (Module module : manager.getModules()) {
         this.before.put(module, module.isSavedEnabled());
         this.inspect(module);
      }

      for (Module module : manager.getModules()) {
         if (!(module instanceof ClickGuiModule) && !module.selfTestExempt()) {
            module.setHeld(true);
            if (module.isSavedEnabled()) {
               this.started.add(module);
            } else {
               this.refused.add(module.getName());
            }
         } else {
            this.skipped++;
         }
      }

      CommandManager.feedback(
         "§bSelf-test §7running "
            + this.started.size()
            + " modules for 7 seconds"
            + (this.skipped > 0 ? " (" + this.skipped + " skipped)" : "")
            + ". Your own setup is restored afterwards."
      );
   }

   private void inspect(Module module) {
      try {
         for (Setting<?> setting : module.settings()) {
            setting.get();
            setting.visible();
            setting.isDefault();
            setting.description();
         }

         module.info();
      } catch (RuntimeException | LinkageError var4) {
         this.problems.add("§c" + module.getName() + " §7settings or info: " + describe(var4));
         Gemini.LOGGER.error("Self-test: {} threw while reading its settings", module.getName(), var4);
      }
   }

   private void finish(ModuleManager manager, String abortReason) {
      current = null;
      long measured = this.measureStart == 0L ? 0L : System.nanoTime() - this.measureStart;
      Set<String> crashed = new HashSet<>();

      for (ModuleManager.Failure failure : ModuleManager.drainFailures()) {
         crashed.add(failure.module());
         this.problems.add("§c" + failure.module() + " §7" + failure.hook() + ": " + failure.error());
      }

      List<String> stoppedAlone = new ArrayList<>();
      int clean = 0;

      for (Module module : this.started) {
         if (!crashed.contains(module.getName())) {
            if (module.isSavedEnabled()) {
               clean++;
            } else {
               stoppedAlone.add(module.getName());
            }
         }
      }

      for (Entry<Module, Boolean> entry : this.before.entrySet()) {
         Module modulex = entry.getKey();
         modulex.setHeld(entry.getValue() && !crashed.contains(modulex.getName()));
      }

      Gemini.getInstance().config().setSavingPaused(false);
      List<String> lines = new ArrayList<>();
      String summary = "§bSelf-test §7"
         + (abortReason == null ? "finished" : "stopped early (" + abortReason + ")")
         + ": §a"
         + clean
         + " ran clean§7, §e"
         + stoppedAlone.size()
         + " turned themselves off§7, §c"
         + crashed.size()
         + " crashed"
         + (this.refused.isEmpty() ? "" : "§7, §e" + this.refused.size() + " refused to start");
      lines.add(summary);
      int shown = 0;

      for (String problem : this.problems) {
         if (shown++ >= 8) {
            lines.add("§7... and " + (this.problems.size() - 8) + " more in the log");
            break;
         }

         lines.add(problem);
      }

      if (!stoppedAlone.isEmpty()) {
         lines.add("§eTurned themselves off: §7" + String.join(", ", stoppedAlone.subList(0, Math.min(stoppedAlone.size(), 8))));
      }

      if (!this.refused.isEmpty()) {
         lines.add("§eRefused to start: §7" + String.join(", ", this.refused.subList(0, Math.min(this.refused.size(), 8))));
      }

      String heaviest = this.heaviest(measured);
      if (heaviest != null) {
         lines.add(heaviest);
      }

      for (String line : lines) {
         CommandManager.feedback(line);
         Gemini.LOGGER.info("Self-test: {}", line.replaceAll("§.", ""));
      }

      for (String problem : this.problems) {
         Gemini.LOGGER.warn("Self-test problem: {}", problem.replaceAll("§.", ""));
      }
   }

   private String heaviest(long measuredNanos) {
      if (measuredNanos > 0L && !this.cost.isEmpty()) {
         List<Entry<Module, long[]>> ranked = new ArrayList<>(this.cost.entrySet());
         ranked.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
         double seconds = measuredNanos / 1.0E9;
         StringBuilder text = new StringBuilder("§bHeaviest per second §7(ms of CPU): ");
         int count = 0;

         for (Entry<Module, long[]> entry : ranked) {
            if (count++ >= 5) {
               break;
            }

            double perSecond = entry.getValue()[0] / 1000000.0 / seconds;
            text.append(count > 1 ? "§7, " : "")
               .append("§f")
               .append(entry.getKey().getName())
               .append(" §7")
               .append(String.format(Locale.ROOT, "%.2f", perSecond));
         }

         return text.toString();
      } else {
         return null;
      }
   }

   private static String describe(Throwable exception) {
      return exception.getClass().getSimpleName() + ": " + exception.getMessage();
   }
}
