package gemini.mixin.lock;

import gemini.Gemini;
import gemini.util.SingleplayerGate;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The net under the screens: while the sandbox is on, no TCP connection to a server is opened by anything. The
 * vanilla screens never get this far (the connect screens and the server list are replaced by an explanation
 * first), so reaching it means another mod tried to connect; it gets an exception that says why, and the log
 * names the address.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
   @Inject(
      method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
      at = @At("HEAD")
   )
   private static void gemini$sandboxGate(
      InetSocketAddress address, EventLoopGroupHolder group, Connection connection, CallbackInfoReturnable<ChannelFuture> cir
   ) {
      if (SingleplayerGate.connectionsBlocked()) {
         Gemini.LOGGER.warn("Refused a connection to {}: {}", address == null ? "a server" : address.getHostString(), SingleplayerGate.BLOCKED_REASON);
         throw new IllegalStateException(SingleplayerGate.BLOCKED_REASON);
      }
   }
}
