package gemini.ui.clickgui.window;

import java.util.List;
import java.util.Locale;

/**
 * Small pure helpers behind the Baritone page: which bot module a running task belongs to, progress and ETA.
 */
public final class BotTasks {
   /** Bot modules that hand a task to the pathing engine, in the order ownership is resolved. */
   public static final List<String> PATHING_MODULES = List.of("Auto Goto", "Auto Mine Bot", "Auto Explore", "Follow Bot", "Auto Tunnel", "Auto Excavate");

   private BotTasks() {
   }

   /** The module that starts the given task kind (a {@code BuiltInBaritone.TaskKind} label), or null. */
   public static String moduleFor(String taskLabel) {
      if (taskLabel == null) {
         return null;
      }

      return switch (taskLabel.toLowerCase(Locale.ROOT)) {
         case "goto" -> "Auto Goto";
         case "mine" -> "Auto Mine Bot";
         case "explore" -> "Auto Explore";
         case "follow" -> "Follow Bot";
         case "tunnel" -> "Auto Tunnel";
         case "excavate" -> "Auto Excavate";
         default -> null;
      };
   }

   /**
    * Which enabled bot module owns the running task: the module that starts this kind of task, when it is on;
    * for a generic label (Baritone's "Pathing") the first enabled pathing module. Null when no matching module is
    * on, which means a command or another mod started the task.
    */
   public static String owner(String taskLabel, List<String> enabledModules) {
      if (taskLabel == null || enabledModules == null || enabledModules.isEmpty()) {
         return null;
      }

      String direct = moduleFor(taskLabel);
      if (direct != null) {
         return enabledModules.contains(direct) ? direct : null;
      }

      for (String name : PATHING_MODULES) {
         if (enabledModules.contains(name)) {
            return name;
         }
      }

      return null;
   }

   /** Fraction (0..1) of a path walked; 0 for an empty path. */
   public static float progress(int index, int size) {
      if (size <= 0) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(1.0F, (float)index / size));
   }

   /** Seconds to cover {@code distance} blocks at {@code speed} blocks per second, or -1 while (nearly) standing still. */
   public static double etaSeconds(double distance, double speed) {
      if (!(distance >= 0.0) || !(speed > 0.3)) {
         return -1.0;
      }

      return distance / speed;
   }

   /** "45s", "3m 05s", "1h 02m", or "--" for an unknown time. */
   public static String formatDuration(double seconds) {
      if (!(seconds >= 0.0) || Double.isInfinite(seconds)) {
         return "--";
      }

      long s = Math.round(seconds);
      if (s < 60L) {
         return s + "s";
      } else if (s < 3600L) {
         return String.format(Locale.ROOT, "%dm %02ds", s / 60L, s % 60L);
      } else {
         return String.format(Locale.ROOT, "%dh %02dm", s / 3600L, s / 60L % 60L);
      }
   }

   /** Exponential average for the measured walking speed: moves {@code factor} of the way to the new sample. */
   public static double smooth(double previous, double sample, double factor) {
      if (Double.isNaN(previous)) {
         return sample;
      }

      double f = Math.max(0.0, Math.min(1.0, factor));
      return previous + (sample - previous) * f;
   }
}
