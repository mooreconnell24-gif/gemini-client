package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EdgeJump extends Module {
   private final NumberSetting edgeOffset = this.addSetting(
      new NumberSetting("Edge Threshold", "How close to the block edge before jumping", 0.18, 0.05, 0.4, 0.02)
   );

   public EdgeJump() {
      super("EdgeJump", "Auto-jump at the outer edge of blocks.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client) && client.player.onGround() && !client.player.isShiftKeyDown() && MoveUtil.hasInput(client.player)) {
         LocalPlayer player = client.player;
         Vec3 motion = player.getDeltaMovement();
         AABB nextBox = player.getBoundingBox().move(motion.x, -0.5, motion.z).deflate(this.edgeOffset.get(), 0.0, this.edgeOffset.get());
         if (client.level.noCollision(player, nextBox)) {
            player.jumpFromGround();
         }
      }
   }
}
