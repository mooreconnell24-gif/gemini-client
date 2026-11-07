package com.inferno.fabric.mixin;

import com.inferno.fabric.InfernoMod;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonNetworkHandlerMixin {
   @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
   private void inferno$send(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      if ((Object)this instanceof ServerGamePacketListenerImpl handler && handler.player != null) {
         MinecraftServer server = InfernoMod.server;
         if (server != null && server.isSameThread()) {
            try {
               InfernoMod.ENGINE.packetSent(handler.player, packet);
            } catch (RuntimeException e) {
               InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
            }
         }
      }
   }
}
