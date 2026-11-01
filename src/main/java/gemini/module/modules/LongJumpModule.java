package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class LongJumpModule extends Module {
   private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Horizontal launch speed multiplier", 1.8, 1.1, 4.0, 0.1));
   private final NumberSetting lift = this.addSetting(new NumberSetting("Lift", "Extra upward speed on launch", 0.0, 0.0, 0.5, 0.05));
   private boolean wasOnGround = true;

   public LongJumpModule() {
      super("LongJump", "Launch further when you jump.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         boolean launched = this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.1;
         this.wasOnGround = player.onGround();
         if (launched && MoveUtil.hasInput(player)) {
            MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * this.boost.get());
            if (this.lift.get() > 0.0) {
               Vec3 motion = player.getDeltaMovement();
               player.setDeltaMovement(motion.x, motion.y + this.lift.get(), motion.z);
            }
         }
      }
   }
}
