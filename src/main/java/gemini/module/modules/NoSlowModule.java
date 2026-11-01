package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class NoSlowModule extends Module {
   private final NumberSetting strength = this.addSetting(
      new NumberSetting("Strength", "How much of the item-use slowdown is removed (%)", 100.0, 10.0, 100.0, 5.0)
   );

   public NoSlowModule() {
      super("NoSlow", "Remove the slowdown from eating, blocking and drawing a bow.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.isUsingItem() && !player.isPassenger() && player.onGround() && MoveUtil.hasInput(player)) {
         double normal = MoveUtil.baseSpeed(player);
         double slowed = normal * 0.2;
         double target = slowed + (normal - slowed) * (this.strength.get() / 100.0);
         if (MoveUtil.horizontalSpeed(player) < target) {
            MoveUtil.setHorizontal(player, target);
         }
      }
   }
}
