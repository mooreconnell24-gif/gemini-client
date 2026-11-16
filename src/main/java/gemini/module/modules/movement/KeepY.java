package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class KeepY extends Module {
   private final BooleanSetting boost = this.addSetting(new BooleanSetting("Air Strafe", "Keep at least walking speed while in the air", true));
   private int lockedY;

   public KeepY() {
      super("Auto Hop", "Jumps every time you land while moving.", Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      this.lockedY = player == null ? 0 : player.getBlockY();
   }

   @Override
   public String info() {
      return "Y=" + this.lockedY;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && MoveUtil.hasInput(client.player)) {
         LocalPlayer player = client.player;
         if (player.onGround()) {
            this.lockedY = player.getBlockY();
            player.jumpFromGround();
         } else if (this.boost.get()) {
            MoveUtil.setHorizontal(player, Math.max(MoveUtil.horizontalSpeed(player), MoveUtil.baseSpeed(player)));
         }
      }
   }
}
