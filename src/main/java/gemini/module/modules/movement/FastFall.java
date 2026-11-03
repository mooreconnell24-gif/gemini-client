package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class FastFall extends Module {
   private final NumberSetting speed = this.addSetting(new NumberSetting("Fall Speed", "Downward speed in blocks per tick", 1.0, 0.3, 4.0, 0.1));
   private final BooleanSetting onlySafe = this.addSetting(
      new BooleanSetting("Only When Safe", "Only speed up when the whole fall stays under three blocks", true)
   );

   public FastFall() {
      super("Fast Fall", "Drop to the ground faster.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (MovementHelpers.ready(client) && MovementHelpers.airborne(player)) {
         Vec3 motion = player.getDeltaMovement();
         if (!(motion.y > -0.2)) {
            double ground = MovementHelpers.groundDistance(client.level, player, 12);
            if (!(ground < 0.0) && (!this.onlySafe.get() || !(player.fallDistance + ground > 3.0))) {
               double fall = Math.min(this.speed.get(), Math.max(0.1, ground - 0.05));
               player.setDeltaMovement(motion.x, -fall, motion.z);
            }
         }
      }
   }
}
