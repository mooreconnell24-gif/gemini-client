package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoWindCharge extends Module {
   private boolean wasOnGround = true;

   public AutoWindCharge() {
      super("AutoWindCharge", "Fire a Wind Charge downward when jumping.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.25) {
            int slot = Inventories.findHotbar(player, s -> s.is(Items.WIND_CHARGE));
            if (slot >= 0) {
               RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, true, 10, 3.0F);
               int prev = player.getInventory().getSelectedSlot();
               player.getInventory().setSelectedSlot(slot);
               RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
               player.getInventory().setSelectedSlot(prev);
            }
         }

         this.wasOnGround = player.onGround();
      }
   }
}
