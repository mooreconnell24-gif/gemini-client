package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class FastLadderModule extends Module {
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Climbing speed in blocks per tick", 0.3, 0.15, 0.6, 0.01));

   public FastLadderModule() {
      super("FastLadder", "Climb ladders and vines faster.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.input != null && player.onClimbable() && !player.isCrouching()) {
         Vec3 motion = player.getDeltaMovement();
         if (player.input.keyPresses.jump() || player.horizontalCollision && player.input.hasForwardImpulse()) {
            player.setDeltaMovement(motion.x, this.speed.get(), motion.z);
         }
      }
   }
}
