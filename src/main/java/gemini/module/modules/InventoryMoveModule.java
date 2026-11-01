package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class InventoryMoveModule extends Module {
   private final BooleanSetting sprint = this.addSetting(new BooleanSetting("Sprint", "Keep sprint speed while a menu is open", true));
   private final BooleanSetting jump = this.addSetting(new BooleanSetting("Jump", "Allow jumping with the jump key", true));

   public InventoryMoveModule() {
      super("InventoryMove", "Walk and jump while inventories are open.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gui.screen() instanceof AbstractContainerScreen) {
         double forward = (MoveUtil.physicalKeyDown(client.options.keyUp) ? 1 : 0) - (MoveUtil.physicalKeyDown(client.options.keyDown) ? 1 : 0);
         double strafe = (MoveUtil.physicalKeyDown(client.options.keyLeft) ? 1 : 0) - (MoveUtil.physicalKeyDown(client.options.keyRight) ? 1 : 0);
         if (forward != 0.0 || strafe != 0.0) {
            double length = Math.hypot(forward, strafe);
            forward /= length;
            strafe /= length;
            double yaw = Math.toRadians(player.getYRot());
            double speed = this.sprint.get() && forward > 0.0 ? 0.2806 : 0.2158;
            if (!player.onGround()) {
               speed = Math.max(speed, MoveUtil.horizontalSpeed(player));
            }

            double x = (strafe * Math.cos(yaw) - forward * Math.sin(yaw)) * speed;
            double z = (forward * Math.cos(yaw) + strafe * Math.sin(yaw)) * speed;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(x, motion.y, z);
         }

         if (this.jump.get() && player.onGround() && MoveUtil.physicalKeyDown(client.options.keyJump)) {
            player.jumpFromGround();
         }
      }
   }
}
