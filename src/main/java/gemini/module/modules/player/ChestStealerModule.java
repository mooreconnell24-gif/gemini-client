package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

public final class ChestStealerModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between items", 1.0, 0.0, 10.0, 1.0));
   private final BooleanSetting autoClose = this.addSetting(new BooleanSetting("Auto Close", "Close the container once it is empty", true));
   private int cooldown;

   public ChestStealerModule() {
      super("ChestStealer", "Quickly move everything out of chests and shulker boxes.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null) {
         AbstractContainerMenu menu = player.containerMenu;
         if (!(menu instanceof ChestMenu) && !(menu instanceof ShulkerBoxMenu)) {
            this.cooldown = 0;
         } else if (this.cooldown > 0) {
            this.cooldown--;
         } else if (player.getInventory().getFreeSlot() >= 0) {
            boolean anyLeft = false;

            for (Slot slot : menu.slots) {
               if (slot.container != player.getInventory() && slot.hasItem()) {
                  anyLeft = true;
                  client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
                  this.cooldown = this.delay.intValue();
                  if (this.cooldown > 0) {
                     return;
                  }
               }
            }

            if (!anyLeft && this.autoClose.get()) {
               player.closeContainer();
            }
         }
      }
   }
}
