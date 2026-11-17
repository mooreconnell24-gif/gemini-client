package gemini.module;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.diags.MixinReport;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.util.Scheduler;
import gemini.util.SpCommands;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Developer checks that need a running game: {@code .devcheck mixins|commands|func|all}. Only answers in a development
 * environment (the {@code runClient} task); in a release build the command reports that it is unavailable and does
 * nothing. Results go to chat and to the log, every line prefixed with {@code DEVCHECK}.
 */
public final class DevCheck {
   private static final Deque<Step> STEPS = new ArrayDeque<>();
   private static final List<String> RESULTS = new ArrayList<>();
   private static boolean running;

   private DevCheck() {
   }

   public static boolean available() {
      return FabricLoader.getInstance().isDevelopmentEnvironment();
   }

   public static void run(ModuleManager modules, String[] parts) {
      if (!available()) {
         CommandManager.feedback("§c.devcheck is only available in a development build.");
         return;
      } else if (running) {
         CommandManager.feedback("§cA dev check is already running.");
         return;
      } else if (Minecraft.getInstance().player == null) {
         CommandManager.feedback("§cJoin a single-player world first.");
         return;
      }

      String what = parts.length > 1 ? parts[1] : "all";
      RESULTS.clear();
      STEPS.clear();
      if (what.equals("mixins") || what.equals("all")) {
         STEPS.add(new Step(0, DevCheck::mixins));
      }

      if (what.equals("commands") || what.equals("all")) {
         DevCheckCommands.queue(modules);
      }

      if (what.equals("nan") || what.equals("all")) {
         DevCheckSweep.queue(modules);
      }

      if (what.equals("func") || what.equals("all")) {
         DevCheckFunctions.queue(modules, parts.length > 2 ? parts[2] : null);
      }

      STEPS.add(new Step(0, DevCheck::summary));
      running = true;
      next();
   }

   /** Adds a step: run {@code action}, then wait {@code ticks} client ticks before the next step. */
   static void step(int ticks, Runnable action) {
      STEPS.add(new Step(ticks, action));
   }

   private static void next() {
      Step step = STEPS.pollFirst();
      if (step == null) {
         running = false;
         return;
      }

      try {
         if (Minecraft.getInstance().player == null) {
            STEPS.clear();
            running = false;
            return;
         }

         step.action.run();
      } catch (RuntimeException | LinkageError error) {
         fail("harness step threw " + error);
         Gemini.LOGGER.error("DEVCHECK step failed", error);
      }

      Scheduler.later(step.ticks, DevCheck::next);
   }

   static void ok(String line) {
      Gemini.LOGGER.info("DEVCHECK ok {}", line);
   }

   static void fail(String line) {
      RESULTS.add(line);
      Gemini.LOGGER.warn("DEVCHECK FAIL {}", line);
      CommandManager.feedback("§cDEVCHECK §7" + line);
   }

   static void info(String line) {
      Gemini.LOGGER.info("DEVCHECK {}", line);
   }

   private static void mixins() {
      try {
         // Loads every class a mixin targets, so every injection is applied and verified now.
         MixinEnvironment.getCurrentEnvironment().audit();
      } catch (RuntimeException | LinkageError error) {
         fail("mixin audit threw " + error);
      }

      info("mixins applied: " + MixinReport.appliedCount());
      List<String> missed = MixinReport.missed();
      if (missed.isEmpty()) {
         ok("every mixin injection found its target");
      }

      for (String entry : missed) {
         fail("mixin inactive: " + entry);
      }
   }

   private static void summary() {
      for (String failure : SpCommands.recentFailures()) {
         // "Nothing to do" answers (clearing an empty inventory, a rule already at that value) are not failures.
         if (!failure.contains("No items were found") && !failure.contains("no effects to remove") && !failure.contains("is already set to")) {
            fail("command failed: /" + failure);
         }
      }

      String line = "finished: " + RESULTS.size() + " problem(s)";
      Gemini.LOGGER.info("DEVCHECK {}", line);
      CommandManager.feedback("§bDEVCHECK §7" + line);
   }

   // ---- helpers shared by the check lists ----

   static Module module(ModuleManager modules, String name) {
      Module module = modules.getByName(name).orElse(null);
      if (module == null) {
         fail("no module named " + name);
      }

      return module;
   }

   static Setting<?> setting(Module module, String name) {
      for (Setting<?> setting : module.settings()) {
         if (setting.name().equalsIgnoreCase(name)) {
            return setting;
         }
      }

      fail(module.getName() + " has no setting " + name);
      return null;
   }

   static void setMode(Module module, String name, String value) {
      if (setting(module, name) instanceof ModeSetting mode) {
         mode.set(value);
      }
   }

   static void setBool(Module module, String name, boolean value) {
      if (setting(module, name) instanceof BooleanSetting bool) {
         bool.set(value);
      }
   }

   static void setNumber(Module module, String name, double value) {
      if (setting(module, name) instanceof NumberSetting number) {
         number.set(value);
      }
   }

   static void action(Module module, String name) {
      if (setting(module, name) instanceof ActionSetting action) {
         action.run();
      }
   }

   /** Records a check result: {@code problem} returns null when the module did its job. */
   static void expect(String module, Supplier<String> problem) {
      String result;
      try {
         result = problem.get();
      } catch (RuntimeException | LinkageError error) {
         result = "check threw " + error;
      }

      if (result == null) {
         ok(module);
      } else {
         fail(module + ": " + result);
      }
   }

   private record Step(int ticks, Runnable action) {
   }
}
