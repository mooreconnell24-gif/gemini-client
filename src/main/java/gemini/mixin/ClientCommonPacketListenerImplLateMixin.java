package gemini.mixin;

import gemini.util.PacketTunnel;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The same hook as {@link ClientCommonPacketListenerImplMixin} at a high priority, so that between the two Gemini
 * always reaches a packet before another mod's hook at a priority in between; see {@link PacketTunnel}.
 */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 3000)
public abstract class ClientCommonPacketListenerImplLateMixin {
   @Inject(
      method = {"send"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$outgoingLate(Packet<?> packet, CallbackInfo ci) {
      if (PacketTunnel.onSend(this, packet)) {
         ci.cancel();
      }
   }
}
