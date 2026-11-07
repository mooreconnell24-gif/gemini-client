package com.inferno.fabric;

import com.inferno.fabric.admin.AdminService;
import com.inferno.fabric.admin.DiscordWebhook;
import com.inferno.fabric.checks.Check;
import com.inferno.fabric.checks.PlayerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Alerts {
   private static final int LOG_SIZE = 100;
   private static final ConcurrentLinkedDeque<Alerts.Alert> LOG = new ConcurrentLinkedDeque<>();
   private static final Map<String, Map<String, Integer>> VLS = new ConcurrentHashMap<>();
   private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
   private static final Map<String, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
   public static final Queue<String> CHAT = new ConcurrentLinkedQueue<>();
   public static volatile String hostUuid = "";
   private static final boolean IS_CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
   private static final Map<String, Integer> FLAG_COUNTS = new ConcurrentHashMap<>();
   private static final Map<String, Integer> WARNED = new ConcurrentHashMap<>();

   private Alerts() {
   }

   /** Optional observer of every flag (used by the in-game scenario tests). */
   public static volatile java.util.function.BiConsumer<ServerPlayer, Check> listener;

   public static int flagCount(String checkId) {
      return FLAG_COUNTS.getOrDefault(checkId, 0);
   }

   public static void resetFlagCounts() {
      FLAG_COUNTS.clear();
   }

   public static void flag(ServerPlayer player, Check check, String detail) {
      java.util.function.BiConsumer<ServerPlayer, Check> l = listener;
      if (l != null) {
         l.accept(player, check);
      }

      FLAG_COUNTS.merge(check.id(), 1, Integer::sum);
      String uuid = player.getStringUUID();
      PlayerState state = InfernoMod.ENGINE.state(player);
      state.lastFlag = check.displayName() + ": " + detail;
      state.lastFlagTime = System.currentTimeMillis();
      String name = player.getName().getString();
      NAMES.put(uuid, name);
      int vl = VLS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).merge(check.id(), 1, Integer::sum);
      long now = System.currentTimeMillis();
      Long last = LAST_MESSAGE.get(uuid + check.id());
      if (last == null || now - last >= 1000L) {
         LAST_MESSAGE.put(uuid + check.id(), now);
         LOG.addFirst(new Alerts.Alert(name, check.displayName(), detail, vl));

         while (LOG.size() > 100) {
            LOG.pollLast();
         }

         String line = "§8[§6§lInferno§8] §f" + name + " §7failed §c" + check.displayName() + " §8(§7" + detail + "§8) §cVL " + vl;
         String plain = line.replaceAll("§.", "");
         InfernoMod.LOGGER.info(plain);
         AlertFile.write(plain);
         DiscordWebhook.send(plain);
         if (InfernoConfig.get().chatAlerts && IS_CLIENT && CHAT.size() < 50) {
            CHAT.add(line);
         }

         InfernoConfig cfg = InfernoConfig.get();
         warn(player, check, vl, cfg);
         boolean host = uuid.equals(hostUuid);
         if (vl >= cfg.kickVl && !host) {
            String mode = cfg.autoPunish == null ? "none" : cfg.autoPunish.toLowerCase(Locale.ROOT);
            if ("ban".equals(mode) || cfg.banOnKickVl) {
               VLS.get(uuid).put(check.id(), 0);
               AdminService.ban(player, "Auto-ban: " + check.displayName(), "Inferno", cfg.defaultBanMinutes);
               CHAT.add("§8[§6§lInferno§8] §c" + name + " was banned for " + check.displayName() + ".");
            } else if ("kick".equals(mode) || cfg.kickLanCheaters) {
               VLS.get(uuid).put(check.id(), 0);
               player.connection.disconnect(Component.literal("§c§lInferno Anti Cheat\n\n§7Removed for: §f" + check.displayName()));
               CHAT.add("§8[§6§lInferno§8] §c" + name + " was kicked for " + check.displayName() + ".");
            }
         }
      }
   }

   private static void warn(ServerPlayer player, Check check, int vl, InfernoConfig cfg) {
      if (cfg.warnPlayers) {
         String key = player.getStringUUID() + check.id();
         int warnAt = Math.max(1, (int)Math.ceil(cfg.kickVl * 0.5));
         int finalAt = Math.max(warnAt + 1, (int)Math.ceil(cfg.kickVl * 0.8));
         int stage = vl >= finalAt ? 2 : (vl >= warnAt ? 1 : 0);
         if (stage == 0) {
            WARNED.remove(key);
         } else if (stage > WARNED.getOrDefault(key, 0)) {
            WARNED.put(key, stage);
            String head = stage == 2 ? "§4§l⚠ FINAL WARNING" : "§c§l⚠ WARNING";
            player.sendSystemMessage(
               Component.literal(
                  "§8[§6§lInferno§8] "
                     + head
                     + " §7Inferno detected §f"
                     + check.displayName()
                     + "§7. Turn off any cheats"
                     + (stage == 2 ? ", or you will be kicked very soon." : ".")
               )
            );
            player.sendOverlayMessage(Component.literal(head + " §7- §f" + check.displayName()));
         }
      }
   }

   public static List<Alerts.Alert> recent() {
      return new ArrayList<>(LOG);
   }

   public static void clearLog() {
      LOG.clear();
   }

   public static void test() {
      LOG.addFirst(new Alerts.Alert("You", "Test alert", "this is only a test, nothing was detected", 0));

      while (LOG.size() > 100) {
         LOG.pollLast();
      }

      if (IS_CLIENT) {
         CHAT.add("§8[§6§lInferno§8] §f(test) §7Alerts show up here in chat, and in the panel's Alerts tab.");
      }
   }

   public static Map<String, Integer> violations(String uuid) {
      Map<String, Integer> v = VLS.get(uuid);
      return v == null ? new HashMap<>() : new HashMap<>(v);
   }

   public static void clear(String uuid) {
      VLS.remove(uuid);
      WARNED.keySet().removeIf(k -> k.startsWith(uuid));
   }

   public static void setViolation(String uuid, String checkId, int vl) {
      if (vl <= 0) {
         Map<String, Integer> map = VLS.get(uuid);
         if (map != null) {
            map.remove(checkId);
            if (map.isEmpty()) {
               VLS.remove(uuid);
            }
         }
      } else {
         VLS.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(checkId, vl);
      }
   }

   public static int suspicion(String uuid) {
      double score = 0.0;
      int limit = Math.max(1, InfernoConfig.get().kickVl);

      for (int v : violations(uuid).values()) {
         score += (double)v / limit;
      }

      return (int)Math.min(100L, Math.round(score * 100.0));
   }

   public static void decay() {
      for (Map<String, Integer> v : VLS.values()) {
         v.replaceAll((k, n) -> Math.max(0, n - 1));
         v.values().removeIf(n -> n == 0);
      }

      VLS.values().removeIf(Map::isEmpty);
   }

   public static void forget(String uuid) {
      LAST_MESSAGE.keySet().removeIf(k -> k.startsWith(uuid));
      WARNED.keySet().removeIf(k -> k.startsWith(uuid));
      if (!InfernoConfig.get().persistVlOnQuit) {
         VLS.remove(uuid);
      }
   }

   public static final class Alert {
      public final long time = System.currentTimeMillis();
      public final String player;
      public final String check;
      public final String detail;
      public final int vl;

      Alert(String player, String check, String detail, int vl) {
         this.player = player;
         this.check = check;
         this.detail = detail;
         this.vl = vl;
      }
   }
}
