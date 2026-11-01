package gemini.util;

import gemini.Gemini;
import java.util.ArrayList;
import java.util.List;

public final class Scheduler {
   private static final List<Scheduler.Job> JOBS = new ArrayList<>();

   private Scheduler() {
   }

   public static void later(int ticks, Runnable action) {
      JOBS.add(new Scheduler.Job(Math.max(0, ticks), action));
   }

   public static void tick() {
      if (!JOBS.isEmpty()) {
         List<Scheduler.Job> due = null;

         for (int i = JOBS.size() - 1; i >= 0; i--) {
            Scheduler.Job job = JOBS.get(i);
            if (job.remaining-- <= 0) {
               if (due == null) {
                  due = new ArrayList<>();
               }

               due.add(0, job);
               JOBS.remove(i);
            }
         }

         if (due != null) {
            for (Scheduler.Job job : due) {
               try {
                  job.action.run();
               } catch (RuntimeException var4) {
                  Gemini.LOGGER.warn("A scheduled job failed", var4);
               }
            }
         }
      }
   }

   public static void clear() {
      JOBS.clear();
   }

   private static final class Job {
      private int remaining;
      private final Runnable action;

      private Job(int remaining, Runnable action) {
         this.remaining = remaining;
         this.action = action;
      }
   }
}
