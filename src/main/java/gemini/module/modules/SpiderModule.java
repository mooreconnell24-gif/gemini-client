package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class SpiderModule extends Module {
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Climb style (Motion or Vulcan)", "Vulcan", "Motion", "Vulcan"));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Climb speed", 0.3, 0.1, 0.8, 0.05));
   private final BooleanSetting requireForward = this.addSetting(new BooleanSetting("Require Forward", "Only climb while pushing into the wall", true));

   public SpiderModule() {
      super("Spider", "Climb walls like a spider.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null
         && player.horizontalCollision
         && !player.isInWater()
         && !player.isInLava()
         && (!this.requireForward.get() || player.input != null && player.input.hasForwardImpulse())) {
         Vec3 motion = player.getDeltaMovement();
         if (this.mode.is("Vulcan")) {
            double y = player.tickCount % 4 == 0 ? 0.42 : -0.04;
            player.setDeltaMovement(motion.x, y, motion.z);
         } else {
            player.setDeltaMovement(motion.x, this.speed.get(), motion.z);
         }
         player.resetFallDistance();
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
