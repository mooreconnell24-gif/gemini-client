package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class TimerRange extends Module {
   private final NumberSetting boost = this.addSetting(
      new NumberSetting("Burst Speed", "Horizontal burst multiplier when closing gap", 1.45, 1.1, 2.2, 0.05)
   );
   private int cooldown;

   public TimerRange() {
      super("TimerRange", "Burst speed briefly when closing gap to a target.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (ClientState.ready(client) && client.player.onGround() && MoveUtil.hasInput(client.player)) {
         LivingEntity target = TargetStrafe.findNearestTarget(client, client.player, 6.0);
         if (target != null && client.player.distanceTo(target) > 3.1) {
            MoveUtil.setHorizontal(client.player, MoveUtil.baseSpeed(client.player) * this.boost.get());
            this.cooldown = 14;
         }
      }
   }
}
