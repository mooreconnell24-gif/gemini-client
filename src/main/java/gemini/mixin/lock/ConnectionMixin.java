package gemini.mixin.lock;

import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Connection.class})
public abstract class ConnectionMixin {
   @Inject(
      method = {"connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"},
      at = {@At("HEAD")}
   )
   private static void gemini$refuseRemote(
      InetSocketAddress address, EventLoopGroupHolder group, Connection connection, CallbackInfoReturnable<ChannelFuture> cir
   ) {
      throw new IllegalStateException(
         "Gemini Client only runs in single-player worlds, so server connections are blocked while it is installed. Remove the Gemini jar from your mods folder to play on servers."
      );
   }
}
