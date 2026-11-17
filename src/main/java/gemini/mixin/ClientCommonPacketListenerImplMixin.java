package gemini.mixin;

import gemini.Gemini;
import gemini.util.MovePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientCommonPacketListenerImpl.class})
public abstract class ClientCommonPacketListenerImplMixin {
   @Inject(
      method = {"send"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$outgoing(Packet<?> packet, CallbackInfo ci) {
      if (packet instanceof ServerboundMovePlayerPacket move && MovePackets.invalid(move)) {
         // The server kicks the player out of their own world for a NaN position or rotation; drop it instead.
         MovePackets.dropped(move);
         ci.cancel();
         return;
      }

      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null && Minecraft.getInstance().player != null && gemini.modules().sendPacket(packet)) {
         ci.cancel();
      }
   }
}
