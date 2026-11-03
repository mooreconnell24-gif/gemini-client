package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BowItem;

public final class Quiver extends Module {
   private final NumberSetting charge = this.addSetting(new NumberSetting("Charge Ticks", "Ticks to draw bow upward", 4.0, 3.0, 10.0, 1.0));

   public Quiver() {
      super("Quiver", "Shoot beneficial tipped arrows upward to buff yourself.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (CombatHelpers.ready(client) && client.gui.screen() == null && player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem) {
         RotationManager.request(player, player.getYRot(), -90.0F, 180.0F, true, 10, 2.0F);
         if (player.getTicksUsingItem() >= this.charge.intValue()) {
            client.gameMode.releaseUsingItem(player);
            this.setEnabled(false);
         }
      }
   }
}
