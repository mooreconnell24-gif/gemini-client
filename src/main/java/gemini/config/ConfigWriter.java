package gemini.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Writes config files on one background thread so a save (which forces the file to disk) never stalls a game tick.
 *
 * <p>Saves are coalesced per file: if a newer version of a file is submitted while an older one is still waiting,
 * only the newer one is written. Writes for one file never overlap and always land in submission order. A write
 * that fails stays queued and is retried on the next {@link #retryFailed()} instead of being dropped.
 */
final class ConfigWriter {
   private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Gemini-Config-IO");
      thread.setDaemon(true);
      return thread;
   });
   private final Object lock = new Object();
   /** Latest content per file that still has to reach the disk. */
   private final Map<Path, Pending> pending = new LinkedHashMap<>();
   private final Map<Path, Pending> failed = new LinkedHashMap<>();
   private final BiConsumer<Path, IOException> onFailure;
   private boolean draining;

   ConfigWriter(BiConsumer<Path, IOException> onFailure) {
      this.onFailure = onFailure;
   }

   void submit(Path target, String content, boolean keepBackup) {
      synchronized (this.lock) {
         this.failed.remove(target);
         this.pending.put(target, new Pending(content, keepBackup));
         if (!this.draining) {
            this.draining = true;
            this.io.execute(this::drain);
         }
      }
   }

   /** Queues every write that failed earlier for another attempt. Returns how many were re-queued. */
   int retryFailed() {
      synchronized (this.lock) {
         int count = this.failed.size();
         if (count > 0) {
            for (Map.Entry<Path, Pending> entry : this.failed.entrySet()) {
               this.pending.putIfAbsent(entry.getKey(), entry.getValue());
            }

            this.failed.clear();
            if (!this.draining) {
               this.draining = true;
               this.io.execute(this::drain);
            }
         }

         return count;
      }
   }

   boolean hasFailures() {
      synchronized (this.lock) {
         return !this.failed.isEmpty();
      }
   }

   private void drain() {
      while (true) {
         Path target;
         Pending next;
         synchronized (this.lock) {
            if (this.pending.isEmpty()) {
               this.draining = false;
               this.lock.notifyAll();
               return;
            }

            target = this.pending.keySet().iterator().next();
            next = this.pending.remove(target);
         }

         try {
            JsonFiles.writeAtomic(target, next.content, next.keepBackup);
         } catch (IOException e) {
            synchronized (this.lock) {
               // A newer save for the same file supersedes the failed one.
               if (!this.pending.containsKey(target)) {
                  this.failed.put(target, next);
               }
            }

            this.onFailure.accept(target, e);
         } catch (RuntimeException e) {
            this.onFailure.accept(target, new IOException(e));
         }
      }
   }

   /** Blocks until everything submitted so far has been written (or failed), or the timeout runs out. */
   boolean awaitIdle(long timeoutMs) {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      synchronized (this.lock) {
         while (this.draining) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
               return false;
            }

            try {
               TimeUnit.NANOSECONDS.timedWait(this.lock, remaining);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return false;
            }
         }

         return true;
      }
   }

   private record Pending(String content, boolean keepBackup) {
   }
}
