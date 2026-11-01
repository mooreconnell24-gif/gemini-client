package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gemini.Gemini;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
   targets = {"net.minecraft.network.PacketProcessor$ListenerAndPacket"}
)
public abstract class PacketProcessorMixin {
   @WrapOperation(
      method = {"handle"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
      )}
   )
   private void gemini$incoming(Packet<?> packet, PacketListener listener, Operation<Void> original) {
      Gemini gemini = Gemini.getInstance();
      if (!(listener instanceof ClientPacketListener)
         || gemini == null
         || gemini.modules() == null
         || Minecraft.getInstance().player == null
         || !gemini.modules().receivePacket(packet)) {
         original.call(new Object[]{packet, listener});
      }
   }
}
