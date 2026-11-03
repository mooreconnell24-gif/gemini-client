package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AutoJump extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Always = bunny hop while moving", "Obstacle", "Always", "Obstacle")
   );

   public AutoJump() {
      super("Auto Jump", "Jump automatically while walking.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (MovementHelpers.ready(client)
         && player.onGround()
         && !player.isShiftKeyDown()
         && !player.isInWater()
         && !player.isPassenger()
         && MoveUtil.hasInput(player)
         && (this.mode.is("Always") || player.horizontalCollision)) {
         player.jumpFromGround();
      }
   }
}
