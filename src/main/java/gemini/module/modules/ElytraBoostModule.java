package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class ElytraBoostModule extends Module {
   private final NumberSetting acceleration = this.addSetting(new NumberSetting("Acceleration", "Boost per tick along your view", 0.08, 0.01, 0.3, 0.01));
   private final NumberSetting maxSpeed = this.addSetting(new NumberSetting("Max Speed", "Top speed in blocks per tick", 1.6, 0.5, 4.0, 0.1));
   private final BooleanSetting requireForward = this.addSetting(new BooleanSetting("Hold Forward", "Only boost while holding forward", true));

   public ElytraBoostModule() {
      super("ElytraBoost", "Accelerate while gliding with an elytra.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.isFallFlying() && (!this.requireForward.get() || player.input != null && player.input.hasForwardImpulse())) {
         Vec3 motion = player.getDeltaMovement();
         if (!(motion.length() >= this.maxSpeed.get())) {
            Vec3 look = player.getLookAngle();
            double a = this.acceleration.get();
            player.setDeltaMovement(motion.add(look.x * a, look.y * a, look.z * a));
         }
      }
   }
}
