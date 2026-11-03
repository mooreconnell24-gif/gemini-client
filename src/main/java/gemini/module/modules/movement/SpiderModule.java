package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class SpiderModule extends Module {
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
         player.setDeltaMovement(motion.x, this.speed.get(), motion.z);
         player.resetFallDistance();
      }
   }

   @Override
   public String info() {
      return String.format("%.2f", this.speed.get());
   }
}
