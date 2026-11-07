package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Enforcement {
   private Enforcement() {
   }

   public static boolean locked(PlayerState s) {
      return s.lockUntil > System.currentTimeMillis() || s.lockActive;
   }

   public static void lock(ServerPlayer p, PlayerState s, String reason) {
      InfernoConfig cfg = InfernoConfig.get();
      if (cfg.hardLock) {
         double x = !Double.isNaN(s.safeX) ? s.safeX : p.getX();
         double y = !Double.isNaN(s.safeX) ? s.safeY : p.getY();
         double z = !Double.isNaN(s.safeX) ? s.safeZ : p.getZ();
         s.lockX = x;
         s.lockY = y;
         s.lockZ = z;
         s.lockActive = true;
         s.cleanGroundTicks = 0;
         s.lockUntil = System.currentTimeMillis() + Math.max(1000L, cfg.lockMinMs);
         s.lockReason = reason == null ? "movement" : reason;
         snap(p, s);
         p.setDeltaMovement(0.0, 0.0, 0.0);
         p.setSprinting(false);
         if (p.getAbilities().flying && !p.isCreative() && !p.isSpectator()) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
         }

         p.sendOverlayMessage(Component.literal("§c§lLOCKED §7- disable cheats & stand still on ground (" + s.lockReason + ")"));
         p.sendSystemMessage(Component.literal("§8[§6§lInferno§8] §cMovement locked. Turn off fly/speed modules, then stand on ground."));
      }
   }

   public static void unlock(PlayerState s) {
      s.lockActive = false;
      s.lockUntil = 0L;
      s.cleanGroundTicks = 0;
      s.lockReason = "";
   }

   public static void snap(ServerPlayer p, PlayerState s) {
      p.connection.teleport(s.lockX, s.lockY, s.lockZ, p.getYRot(), p.getXRot());
      p.setDeltaMovement(0.0, 0.0, 0.0);
      s.lastSetback = System.currentTimeMillis();
   }

   public static boolean applyLock(ServerPlayer p, PlayerState s, boolean onGround) {
      if (!locked(s)) {
         return false;
      }

      snap(p, s);
      if (onGround) {
         s.cleanGroundTicks++;
         if (s.cleanGroundTicks >= InfernoConfig.get().lockCleanTicks) {
            unlock(s);
            p.sendOverlayMessage(Component.literal("§aUnlocked §7- stay legit"));
            return false;
         }
      } else {
         s.cleanGroundTicks = 0;
         s.lockUntil = Math.max(s.lockUntil, System.currentTimeMillis() + 500L);
      }

      return true;
   }

   public static void onMovementViolation(ServerPlayer p, PlayerState s, String detail) {
      s.cleanGroundTicks = 0;
      lock(p, s, detail);
   }
}
