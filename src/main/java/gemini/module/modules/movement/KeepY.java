package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class KeepY extends Module {
   private final BooleanSetting boost = this.addSetting(new BooleanSetting("Air Strafe", "Maintain sprint air speed while jumping", true));
   private int lockedY;

   public KeepY() {
      super("KeepY", "Auto-jump on landing to lock your Y level.", Category.MOVEMENT);
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
      if (MovementHelpers.ready(client) && MoveUtil.hasInput(client.player)) {
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
