package gemini.mixin;

import gemini.Gemini;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class ClientCommonPacketListenerImplMixin {
   @Inject(
      method = {"send"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gemini$outgoing(Packet<?> packet, CallbackInfo ci) {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null && Minecraft.getInstance().player != null && gemini.modules().sendPacket(packet)) {
         ci.cancel();
      }
   }
}
