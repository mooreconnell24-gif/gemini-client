package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.event.ActionPriority;
import gemini.util.Hands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoGappleModule extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Eat when at or below this HP", 10.0, 2.0, 20.0, 1.0));
   private final BooleanSetting enchanted = this.addSetting(new BooleanSetting("Enchanted", "Also eat enchanted golden apples", true));
   private final BooleanSetting restore = this.addSetting(new BooleanSetting("Restore Slot", "Go back to the previous hotbar slot afterwards", true));
   private int previousSlot = -1;
   private boolean eating;

   public AutoGappleModule() {
      super("AutoGapple", "Eat a golden apple when low on health.", Category.COMBAT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public void onDisable() {
      this.finish(Minecraft.getInstance());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null) {
         if (!this.eating) {
            if (!(player.getHealth() > this.health.floatValue()) && !player.isUsingItem() && client.gui.screen() == null) {
               int slot = Inventories.findHotbar(
                  player, stack -> stack.is(Items.GOLDEN_APPLE) || this.enchanted.get() && stack.is(Items.ENCHANTED_GOLDEN_APPLE)
               );
               if (slot >= 0 && Hands.claimMainHand()) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
                  player.getInventory().setSelectedSlot(slot);
                  client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                  client.options.keyUse.setDown(true);
                  this.eating = true;
               }
            }
         } else if (player.isUsingItem() && client.gui.screen() == null) {
            Hands.claimMainHand();
            client.options.keyUse.setDown(true);
         } else {
            this.finish(client);
         }
      }
   }

   private void finish(Minecraft client) {
      if (this.eating) {
         this.eating = false;
         client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
         if (this.restore.get() && client.player != null && this.previousSlot >= 0 && Hands.claimMainHand()) {
            client.player.getInventory().setSelectedSlot(this.previousSlot);
         }

         this.previousSlot = -1;
      }
   }
}
