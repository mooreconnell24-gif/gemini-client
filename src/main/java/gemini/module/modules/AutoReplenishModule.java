package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class AutoReplenishModule extends Module {
   private final NumberSetting threshold = this.addSetting(new NumberSetting("Threshold", "Refill a hotbar stack at or below this count", 8.0, 1.0, 32.0, 1.0));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between refills", 5.0, 1.0, 40.0, 1.0));
   private int cooldown;

   public AutoReplenishModule() {
      super("AutoReplenish", "Refill hotbar stacks from your inventory.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null && client.gui.screen() == null && Inventories.inventoryOpenOrClosed(player)) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            for (int hotbar = 0; hotbar < 9; hotbar++) {
               ItemStack target = player.getInventory().getItem(hotbar);
               if (!target.isEmpty() && target.isStackable() && target.getCount() <= this.threshold.intValue() && target.getCount() < target.getMaxStackSize()) {
                  for (int source = 9; source < 36; source++) {
                     ItemStack candidate = player.getInventory().getItem(source);
                     if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target)) {
                        int container = player.inventoryMenu.containerId;
                        client.gameMode.handleContainerInput(container, Inventories.menuSlot(source), 0, ContainerInput.PICKUP, player);
                        client.gameMode.handleContainerInput(container, Inventories.menuSlot(hotbar), 0, ContainerInput.PICKUP, player);
                        if (!player.inventoryMenu.getCarried().isEmpty()) {
                           client.gameMode.handleContainerInput(container, Inventories.menuSlot(source), 0, ContainerInput.PICKUP, player);
                        }

                        this.cooldown = this.delay.intValue();
                        return;
                     }
                  }
               }
            }
         }
      }
   }
}
