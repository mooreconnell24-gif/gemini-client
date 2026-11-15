package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.util.Hands;
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
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.25) {
            int slot = Inventories.findHotbar(player, s -> s.is(Items.WIND_CHARGE));
            if (slot >= 0 && Hands.claimMainHand()) {
               RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, true, ActionPriority.CRITICAL_SURVIVAL, 3.0F);
               Inventories.withHotbarSlot(player, slot, () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND)));
            }
         }

         this.wasOnGround = player.onGround();
      }
   }
}
