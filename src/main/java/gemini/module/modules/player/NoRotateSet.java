package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;

public final class NoRotateSet extends Module {
   private float savedYaw;
   private float savedPitch;
   private boolean restorePending;

   public NoRotateSet() {
      super("NoRotateSet", "Block server packets from snapping your camera.", Category.PLAYER);
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      Minecraft client = Minecraft.getInstance();
      if (packet instanceof ClientboundPlayerPositionPacket && client.player != null) {
         this.savedYaw = client.player.getYRot();
         this.savedPitch = client.player.getXRot();
         this.restorePending = true;
      }

      return false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.restorePending && client.player != null && client.getConnection() != null) {
         this.restorePending = false;
         client.getConnection().send(new Rot(client.player.getYRot(), client.player.getXRot(), client.player.onGround(), client.player.horizontalCollision));
         client.player.setYRot(this.savedYaw);
         client.player.setXRot(this.savedPitch);
      }
   }
}
