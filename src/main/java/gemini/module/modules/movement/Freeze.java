package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class Freeze extends Module {
   public Freeze() {
      super("Freeze", "Freeze your character mid-air and hold back movement packets.", Category.MOVEMENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         player.setDeltaMovement(Vec3.ZERO);
      }
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      return packet instanceof ServerboundMovePlayerPacket;
   }
}
