package com.inferno.fabric.checks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Packets received per player, counted on the netty thread and drained once a second on the server thread. */
public final class PacketCounter {
   private static final Map<UUID, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

   private PacketCounter() {
   }

   public static void count(UUID player) {
      COUNTS.computeIfAbsent(player, k -> new AtomicInteger()).incrementAndGet();
   }

   /** Returns the count since the last call and starts a new count. */
   public static int drain(UUID player) {
      AtomicInteger n = COUNTS.get(player);
      return n == null ? 0 : n.getAndSet(0);
   }

   public static void forget(UUID player) {
      COUNTS.remove(player);
   }
}
