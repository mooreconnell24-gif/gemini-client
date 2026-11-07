package com.inferno.fabric.admin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExemptStore {
   private static final Map<String, Long> UNTIL = new ConcurrentHashMap<>();

   private ExemptStore() {
   }

   public static void exempt(String uuid, long ms) {
      long until = ms <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() + ms;
      UNTIL.put(uuid, until);
   }

   public static void clear(String uuid) {
      UNTIL.remove(uuid);
   }

   public static boolean isExempt(String uuid) {
      Long until = UNTIL.get(uuid);
      if (until == null) {
         return false;
      } else if (until != Long.MAX_VALUE && System.currentTimeMillis() >= until) {
         UNTIL.remove(uuid);
         return false;
      } else {
         return true;
      }
   }

   public static void forget(String uuid) {
      Long until = UNTIL.get(uuid);
      if (until == null || until != Long.MAX_VALUE) {
         UNTIL.remove(uuid);
      }
   }
}
