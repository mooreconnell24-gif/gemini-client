package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

public final class XCarry extends Module {
   public XCarry() {
      super("XCarry", "Store items in the 2x2 crafting grid.", Category.PLAYER);
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      return packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == 0;
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (client.getConnection() != null && client.player != null) {
         client.getConnection().send(new ServerboundContainerClosePacket(0));
      }
   }
}
