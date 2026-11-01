package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class KeepSprintModule extends Module {
   public KeepSprintModule() {
      super("KeepSprint", "Stay sprinting after attacking.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null
         && player.input != null
         && player.input.hasForwardImpulse()
         && !player.isCrouching()
         && !player.isSprinting()
         && !player.horizontalCollision
         && player.getFoodData().getFoodLevel() > 6
         && !player.isUsingItem()) {
         player.setSprinting(true);
      }
   }
}
