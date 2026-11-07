package com.inferno.fabric.mixin;

import com.inferno.fabric.checks.PacketCounter;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts every packet a player sends, on the netty thread, for the PacketFlood check. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
   @Shadow
   public abstract PacketListener getPacketListener();

   @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
   private void inferno$count(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
      if (this.getPacketListener() instanceof ServerGamePacketListenerImpl game && game.player != null) {
         PacketCounter.count(game.player.getUUID());
      }
   }
}
