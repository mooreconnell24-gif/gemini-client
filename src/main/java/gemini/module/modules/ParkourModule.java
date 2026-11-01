package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ParkourModule extends Module {
   private final BooleanSetting sprintOnly = this.addSetting(new BooleanSetting("Sprint Only", "Only jump while sprinting", false));

   public ParkourModule() {
      super("Parkour", "Jump automatically at the edge of blocks.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null
         && client.level != null
         && client.gui.screen() == null
         && player.onGround()
         && !player.isCrouching()
         && player.input != null
         && player.input.hasForwardImpulse()
         && (!this.sprintOnly.get() || player.isSprinting())) {
         Vec3 motion = player.getDeltaMovement();
         AABB ahead = player.getBoundingBox().move(motion.x * 2.0, -0.6, motion.z * 2.0);
         if (client.level.noCollision(player, ahead)) {
            player.jumpFromGround();
         }
      }
   }
}
