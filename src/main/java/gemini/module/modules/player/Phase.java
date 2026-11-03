package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class Phase extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Walk keeps you grounded", "Walk", "Walk", "Full")
   );
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Movement speed while phasing", 0.2, 0.05, 1.0, 0.01));
   private final BooleanSetting disableOnDrop = this.addSetting(
      new BooleanSetting("Disable On Fall", "Turn off when you leave the block you phased into", false)
   );

   public Phase() {
      super("Phase", "No-clip through blocks.", Category.PLAYER);
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onDisable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         player.noPhysics = false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client)) {
         LocalPlayer player = client.player;
         player.noPhysics = true;
         player.setOnGround(true);
         Vec3 direction = MoveUtil.direction(player);
         if (direction == null) {
            direction = Vec3.ZERO;
         }

         double vertical = 0.0;
         if (this.mode.is("Full")) {
            if (client.options.keyJump.isDown()) {
               vertical = this.speed.get();
            } else if (client.options.keyShift.isDown()) {
               vertical = -this.speed.get();
            }
         } else {
            vertical = client.options.keyShift.isDown() ? -this.speed.get() : 0.0;
         }

         player.setDeltaMovement(direction.x * this.speed.get(), vertical, direction.z * this.speed.get());
         if (this.disableOnDrop.get()
            && client.level.getBlockState(player.blockPosition()).isAir()
            && client.level.getBlockState(player.blockPosition().below()).isAir()) {
            this.setEnabled(false, false);
         }
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
