package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Glide extends Module {
   private final NumberSetting fall = this.addSetting(new NumberSetting("Fall Speed", "Fastest you may fall, in blocks per tick", 0.12, 0.02, 0.5, 0.01));
   private final NumberSetting boost = this.addSetting(new NumberSetting("Speed", "Horizontal speed multiplier while gliding", 1.0, 1.0, 2.0, 0.05));
   private final BooleanSetting noFall = this.addSetting(new BooleanSetting("No Fall Damage", "Reset fall distance while gliding", true));

   public Glide() {
      super("Glide", "Fall slowly, like a feather.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && MovementHelpers.airborne(player)) {
         Vec3 motion = player.getDeltaMovement();
         if (motion.y < -this.fall.get()) {
            player.setDeltaMovement(motion.x, -this.fall.get(), motion.z);
         }

         if (this.boost.get() > 1.0) {
            MoveUtil.setHorizontal(player, 0.2806 * this.boost.get());
         }

         if (this.noFall.get()) {
            AttributeBoost.resetFall(player);
         }
      }
   }
}
