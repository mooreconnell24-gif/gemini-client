package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ReverseStepModule extends Module {
   private final NumberSetting height = this.addSetting(new NumberSetting("Height", "Largest drop to snap down", 3.0, 1.0, 5.0, 0.5));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Downward speed while snapping", 1.0, 0.2, 3.0, 0.1));
   private boolean wasOnGround;

   public ReverseStepModule() {
      super("ReverseStep", "Snap down ledges quickly.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null) {
         boolean leftGround = this.wasOnGround && !player.onGround();
         this.wasOnGround = player.onGround();
         Vec3 motion = player.getDeltaMovement();
         if (leftGround && !(motion.y > 0.0) && !player.isInWater() && !player.isInLava() && !player.getAbilities().flying && !player.isFallFlying()) {
            double maxDrop = this.height.get();
            boolean groundBelow = false;
            for (double dy = 0.5; dy <= maxDrop + 0.05; dy += 0.5) {
               AABB box = player.getBoundingBox().move(0.0, -dy, 0.0);
               if (!client.level.noCollision(player, box)) {
                  groundBelow = true;
                  break;
               }
            }
            if (groundBelow) {
               player.setDeltaMovement(motion.x, -this.speed.get(), motion.z);
            }
         }
      }
   }
}
