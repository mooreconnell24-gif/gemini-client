package gemini.mixin;

import gemini.util.PacketTunnel;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gemini's outgoing packet hook at the default priority; see {@link PacketTunnel}. */
@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class ClientCommonPacketListenerImplMixin {
   @Inject(
      method = {"send"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$outgoing(Packet<?> packet, CallbackInfo ci) {
      if (PacketTunnel.onSend(this, packet)) {
         ci.cancel();
      }
   }
}
