package gemini.module.modules.movement;

import java.util.List;
import java.util.ArrayList;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AutoJump extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Obstacle jumps when you walk into a block; Always jumps whenever you move", "Obstacle", "Always", "Obstacle")
   );

   public AutoJump() {
      super("Auto Jump", "Jumps for you while you walk, either over obstacles or all the time.", Category.MOVEMENT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Auto Hop");
      names.add("KeepY");
      names.add("Bhop");
      return names;
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client)
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
