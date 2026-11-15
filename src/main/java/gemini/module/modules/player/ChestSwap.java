package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class ChestSwap extends Module {
   private boolean done;

   public ChestSwap() {
      super("Chest Swap", "Swap your chestplate and elytra (bind a key to this).", Category.PLAYER);
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   protected boolean hasListOption() {
      return false;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      this.done = false;
   }

   @Override
   public void onTick() {
      if (!this.done) {
         this.done = true;
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (ClientState.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
            this.swap(client, player);
         }

         this.setEnabled(false, false);
      }
   }

   private void swap(Minecraft client, LocalPlayer player) {
      boolean elytraOn = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
      int index = Inventories.find(player, stack -> elytraOn ? stack.is(ItemTags.CHEST_ARMOR) : stack.is(Items.ELYTRA), false);
      if (index < 0) {
         CommandManager.feedback("§bChest Swap §7no " + (elytraOn ? "chestplate" : "elytra") + " in your inventory.");
      } else {
         int container = player.inventoryMenu.containerId;
         int chestSlot = 6;
         if (index < 9) {
            client.gameMode.handleContainerInput(container, chestSlot, index, ContainerInput.SWAP, player);
         } else {
            int menuSlot = Inventories.menuSlot(index);
            client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(container, chestSlot, 0, ContainerInput.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty()) {
               client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
            }
         }
      }
   }
}
