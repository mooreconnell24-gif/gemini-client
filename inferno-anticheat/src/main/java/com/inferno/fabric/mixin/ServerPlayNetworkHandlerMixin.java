package com.inferno.fabric.mixin;

import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.admin.AdminService;
import com.inferno.fabric.admin.ExemptStore;
import com.inferno.fabric.checks.Enforcement;
import com.inferno.fabric.checks.PlayerState;
import java.util.Set;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {
   @Unique
   private boolean inferno$pending;

   @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At("HEAD"), cancellable = true)
   private void inferno$beforeMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      MinecraftServer server = InfernoMod.server;
      if (server != null && server.isSameThread()) {
         ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
         ServerPlayer player = self.player;
         String uuid = player.getStringUUID();
         if (!ExemptStore.isExempt(uuid)) {
            PlayerState state = InfernoMod.ENGINE.state(player);
            if (AdminService.isFrozen(uuid)) {
               player.setDeltaMovement(0.0, 0.0, 0.0);
               player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot(), false);
               this.inferno$pending = false;
               ci.cancel();
            } else if (Enforcement.locked(state)) {
               Enforcement.snap(player, state);
               this.inferno$pending = false;
               ci.cancel();
            } else {
               this.inferno$pending = true;
               InfernoMod.ENGINE.beforeMove(
                  player, packet.hasRotation(), packet.getXRot(0.0F), packet.hasPosition(), packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())
               );
            }
         }
      }
   }

   @Inject(method = "handlePunch(Lnet/minecraft/network/protocol/game/ServerboundPunchPacket;)V", at = @At("RETURN"))
   private void inferno$swing(ServerboundPunchPacket packet, CallbackInfo ci) {
      try {
         ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
         InfernoMod.ENGINE.swing(self.player);
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V", at = @At("HEAD"), cancellable = true)
   private void inferno$clickSlot(ServerboundContainerClickPacket packet, CallbackInfo ci) {
      MinecraftServer server = InfernoMod.server;
      if (server != null && server.isSameThread()) {
         try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
            ServerPlayer player = self.player;
            if (AdminService.isFrozen(player.getStringUUID()) || Enforcement.locked(InfernoMod.ENGINE.state(player))) {
               ci.cancel();
               return;
            }

            InfernoMod.ENGINE.clickSlot(player);
         } catch (RuntimeException e) {
            InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
         }
      }
   }

   @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At("RETURN"))
   private void inferno$afterMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
      if (this.inferno$pending) {
         this.inferno$pending = false;

         try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
            InfernoMod.ENGINE.afterMove(self.player, packet.isOnGround());
         } catch (RuntimeException e) {
            InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
         }
      }
   }

   // ---- Added hooks. Each handler runs twice: first on the netty thread (where
   // PacketUtils.ensureRunningOnSameThread re-queues it) and then on the server thread, so every hook
   // only acts on the server thread, like the hooks above.

   @Unique
   private ServerPlayer inferno$serverThreadPlayer() {
      MinecraftServer server = InfernoMod.server;
      return server != null && server.isSameThread() ? ((ServerGamePacketListenerImpl)(Object)this).player : null;
   }

   @Inject(method = "handlePlayerInput(Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;)V", at = @At("HEAD"))
   private void inferno$input(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.input(player, player.getLastClientInput(), packet.input());
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V", at = @At("HEAD"))
   private void inferno$action(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.playerAction(player, packet.getAction(), packet.getPos());
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handlePlayerCommand(Lnet/minecraft/network/protocol/game/ServerboundPlayerCommandPacket;)V", at = @At("HEAD"))
   private void inferno$command(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.playerCommand(player, packet.getAction());
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V", at = @At("HEAD"))
   private void inferno$carried(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.carriedSlot(player, player.getInventory().getSelectedSlot(), packet.getSlot());
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleClientTickEnd(Lnet/minecraft/network/protocol/game/ServerboundClientTickEndPacket;)V", at = @At("HEAD"))
   private void inferno$tickEnd(ServerboundClientTickEndPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.clientTickEnd(player);
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleAcceptTeleportPacket(Lnet/minecraft/network/protocol/game/ServerboundAcceptTeleportationPacket;)V", at = @At("HEAD"))
   private void inferno$teleportAccepted(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.teleportAccepted(player);
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V", at = @At("HEAD"))
   private void inferno$clientCommand(ServerboundClientCommandPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null && packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            InfernoMod.ENGINE.respawnRequest(player);
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }

   @Inject(method = "handleEditBook(Lnet/minecraft/network/protocol/game/ServerboundEditBookPacket;)V", at = @At("HEAD"))
   private void inferno$editBook(ServerboundEditBookPacket packet, CallbackInfo ci) {
      try {
         ServerPlayer player = this.inferno$serverThreadPlayer();
         if (player != null) {
            InfernoMod.ENGINE.bookEdit(player, packet.pages().size());
         }
      } catch (RuntimeException e) {
         InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
      }
   }
}
