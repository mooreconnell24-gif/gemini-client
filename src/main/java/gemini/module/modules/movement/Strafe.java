package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;

public final class Strafe extends Module {
   private final NumberSetting strength = this.addSetting(new NumberSetting("Strength", "Air strafe responsiveness", 1.0, 0.2, 1.5, 0.05));

   public Strafe() {
      super("Strafe", "Maintain full directional air control while jumping.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && !client.player.onGround() && MoveUtil.hasInput(client.player)) {
         double currentSpeed = Math.max(MoveUtil.horizontalSpeed(client.player), MoveUtil.baseSpeed(client.player) * this.strength.get());
         MoveUtil.setHorizontal(client.player, currentSpeed);
      }
   }
}
