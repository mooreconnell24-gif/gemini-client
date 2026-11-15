package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoSoup extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Eat soup at or below this health", 12.0, 2.0, 19.0, 1.0));
   private final BooleanSetting dropBowl = this.addSetting(new BooleanSetting("Drop Bowl", "Drop empty bowl after eating", true));
   private int previousSlot = -1;
   private boolean eating;

   public AutoSoup() {
      super("AutoSoup", "Eat mushroom stew automatically when low on health.", Category.COMBAT);
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
      if (!ClientState.ready(client) || client.gui.screen() != null) {
         this.finish(client);
         return;
      }

      LocalPlayer player = client.player;
      if (this.eating) {
         // Stew is a normal food now (about 1.6 s to eat), so keep Use held until it is gone.
         if (player.isUsingItem() && player.getUseItem().is(Items.MUSHROOM_STEW) && Hands.claimMainHand()) {
            client.options.keyUse.setDown(true);
            return;
         }

         if (this.dropBowl.get() && player.getMainHandItem().is(Items.BOWL)) {
            client.gameMode.dropItem(player, true);
         }

         this.finish(client);
      } else if (player.getHealth() <= this.health.floatValue() && !player.isUsingItem() && player.canEat(false)) {
         int slot = Inventories.findHotbar(player, s -> s.is(Items.MUSHROOM_STEW));
         if (slot >= 0 && Hands.claimMainHand()) {
            this.previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            client.options.keyUse.setDown(true);
            this.eating = true;
         }
      }
   }

   private void finish(Minecraft client) {
      if (this.eating) {
         this.eating = false;
         client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
         if (client.player != null && this.previousSlot >= 0 && Hands.claimMainHand()) {
            client.player.getInventory().setSelectedSlot(this.previousSlot);
         }

         this.previousSlot = -1;
      }
   }
}
