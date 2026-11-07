package com.inferno.fabric.admin;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.checks.Check;
import com.inferno.fabric.checks.Enforcement;
import com.inferno.fabric.checks.PlayerState;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;

public final class AdminService {
   private static final Set<String> FROZEN = ConcurrentHashMap.newKeySet();

   private AdminService() {
   }

   public static boolean isAdmin(ServerPlayer player) {
      return rank(player).atLeast(AdminRank.HELPER);
   }

   public static boolean canBan(ServerPlayer player) {
      return rank(player).atLeast(AdminRank.ADMIN);
   }

   public static boolean isOwner(ServerPlayer player) {
      return rank(player).atLeast(AdminRank.OWNER);
   }

   public static AdminRank rank(ServerPlayer player) {
      if (player == null) {
         return AdminRank.NONE;
      }

      String uuid = player.getStringUUID();
      AdminRank authed = OpAuth.rankOf(uuid);
      if (authed.atLeast(AdminRank.HELPER)) {
         return authed;
      }

      if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         return AdminRank.ADMIN;
      }

      InfernoConfig cfg = InfernoConfig.get();
      if (cfg.adminUuids != null) {
         for (String id : cfg.adminUuids) {
            if (uuid.equalsIgnoreCase(id)) {
               return AdminRank.ADMIN;
            }
         }
      }

      return AdminRank.NONE;
   }

   public static boolean require(ServerPlayer player, AdminRank min) {
      return rank(player).atLeast(min);
   }

   public static boolean isFrozen(String uuid) {
      return FROZEN.contains(uuid);
   }

   public static void freeze(ServerPlayer target, boolean on) {
      if (on) {
         FROZEN.add(target.getStringUUID());
         target.setDeltaMovement(0.0, 0.0, 0.0);
      } else {
         FROZEN.remove(target.getStringUUID());
      }
   }

   public static void kick(ServerPlayer target, String reason) {
      String msg = reason != null && !reason.isBlank() ? reason : "Removed by Inferno Anti Cheat";
      target.connection.disconnect(Component.literal("§c§lInferno Anti Cheat\n\n§7" + msg));
   }

   public static void ban(ServerPlayer target, String reason, String by, long minutes) {
      BanList.ban(target.getStringUUID(), target.getName().getString(), reason, by, minutes);
      kick(target, reason != null && !reason.isBlank() ? reason : "Banned by Inferno");
   }

   public static boolean unban(String uuidOrName) {
      for (BanList.BanEntry e : BanList.all()) {
         if (e.uuid.equalsIgnoreCase(uuidOrName) || e.name.equalsIgnoreCase(uuidOrName)) {
            return BanList.unban(e.uuid);
         }
      }

      return BanList.unban(uuidOrName);
   }

   public static void teleportTo(ServerPlayer admin, ServerPlayer target) {
      admin.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), target.getYRot(), target.getXRot(), false);
   }

   public static void spectate(ServerPlayer admin, ServerPlayer target) {
      admin.setGameMode(GameType.SPECTATOR);
      teleportTo(admin, target);
   }

   public static void clearVl(String uuid) {
      Alerts.clear(uuid);
   }

   public static void setVl(String uuid, String checkId, int vl) {
      Alerts.setViolation(uuid, checkId, vl);
   }

   public static void exempt(ServerPlayer target, long seconds) {
      ExemptStore.exempt(target.getStringUUID(), seconds <= 0L ? 0L : seconds * 1000L);
   }

   public static void unexempt(ServerPlayer target) {
      ExemptStore.clear(target.getStringUUID());
   }

   public static String statusLine(ServerPlayer p) {
      String uuid = p.getStringUUID();
      return p.getName().getString()
         + " sus="
         + Alerts.suspicion(uuid)
         + "%"
         + (isFrozen(uuid) ? " FROZEN" : "")
         + (ExemptStore.isExempt(uuid) ? " EXEMPT" : "")
         + " combat="
         + InfernoMod.ENGINE.combatSuspicion(uuid)
         + "%";
   }

   public static void toggleCheck(String id, Boolean force) {
      InfernoConfig cfg = InfernoConfig.get();
      if (force == null) {
         cfg.toggle(id);
      } else {
         cfg.checks.put(id, force);
         cfg.save();
      }
   }

   public static void setAllChecks(boolean on) {
      InfernoConfig cfg = InfernoConfig.get();

      for (Check c : InfernoMod.ENGINE.all()) {
         cfg.checks.put(c.id(), on);
      }

      cfg.save();
   }

   public static void leaveCleanup(String uuid) {
      FROZEN.remove(uuid);
      ExemptStore.forget(uuid);
      OpAuth.logout(uuid);
   }

   public static void tickFrozen(MinecraftServer server) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         String uuid = p.getStringUUID();
         if (FROZEN.contains(uuid)) {
            p.setDeltaMovement(0.0, Math.min(0.0, p.getDeltaMovement().y), 0.0);
            p.teleportTo(p.level(), p.getX(), p.getY(), p.getZ(), Set.of(), p.getYRot(), p.getXRot(), false);
         } else {
            PlayerState s = InfernoMod.ENGINE.state(p);
            if (Enforcement.locked(s)) {
               Enforcement.snap(p, s);
            }
         }
      }
   }

   public static String punishMode() {
      return InfernoConfig.get().autoPunish == null ? "none" : InfernoConfig.get().autoPunish.toLowerCase(Locale.ROOT);
   }

   public static void setPunishMode(String mode) {
      InfernoConfig cfg = InfernoConfig.get();
      String m = mode == null ? "none" : mode.toLowerCase(Locale.ROOT);
      if (!m.equals("none") && !m.equals("kick") && !m.equals("ban")) {
         m = "none";
      }

      cfg.autoPunish = m;
      cfg.save();
   }
}
