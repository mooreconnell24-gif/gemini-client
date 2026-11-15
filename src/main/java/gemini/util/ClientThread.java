package gemini.util;

import java.util.concurrent.Executor;

/**
 * The thread Gemini's state belongs to. Module state, the event bus context and every Minecraft object the modules
 * touch are owned by the client (render) thread; work that starts anywhere else (the integrated server, a network
 * thread, a chunk builder, a pathfinder worker) is handed over through {@link #run(Runnable)} instead of touching
 * that state directly.
 *
 * <p>Bound once at start-up. Until then (and in unit tests, which never bind it) every thread counts as the owner.
 */
public final class ClientThread {
   private static volatile Thread owner;
   private static volatile Executor executor;

   private ClientThread() {
   }

   /** Called from the mod initializer, which Fabric runs on the client thread. */
   public static void bind(Thread thread, Executor clientExecutor) {
      owner = thread;
      executor = clientExecutor;
   }

   public static boolean isCurrent() {
      Thread thread = owner;
      return thread == null || Thread.currentThread() == thread;
   }

   /** Runs the task now when already on the client thread, otherwise queues it there. */
   public static void run(Runnable task) {
      Executor target = executor;
      if (target == null || isCurrent()) {
         task.run();
      } else {
         target.execute(task);
      }
   }
}
