package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class NoJumpDelayModule extends Module {
   public NoJumpDelayModule() {
      super("NoJumpDelay", "Jump again the moment you land while holding jump.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null
         && client.gui.screen() == null
         && player.input != null
         && player.onGround()
         && player.input.keyPresses.jump()
         && !player.isInWater()
         && !player.isInLava()) {
         player.jumpFromGround();
      }
   }
}
