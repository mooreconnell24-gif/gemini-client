package gemini.util;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class Inventories {
   private Inventories() {
   }

   public static int findHotbar(LocalPlayer player, Predicate<ItemStack> match) {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (!stack.isEmpty() && match.test(stack)) {
            return i;
         }
      }

      return -1;
   }

   /** The container-menu slot of an inventory index (0-8 hotbar, 9-35 main inventory), or -1 when out of range. */
   public static int menuSlot(int inventoryIndex) {
      if (inventoryIndex < 0) {
         return -1;
      } else if (inventoryIndex < 9) {
         return 36 + inventoryIndex;
      } else {
         return inventoryIndex < 36 ? inventoryIndex : -1;
      }
   }

   /** Swaps an inventory stack with a hotbar slot through the inventory menu, like pressing a number key over it. */
   public static void moveToHotbar(Minecraft client, LocalPlayer player, int inventoryIndex, int hotbarSlot) {
      if (client.gameMode == null || inventoryIndex < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
         return;
      }

      int menuSlot = menuSlot(inventoryIndex);
      if (menuSlot >= 0) {
         client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, hotbarSlot, ContainerInput.SWAP, player);
      }
   }

   /**
    * Runs the action with the given hotbar slot selected and puts the previous selection back afterwards, even
    * when the action throws. Does nothing for a slot outside the hotbar. The caller is expected to have claimed
    * the main hand already.
    */
   public static void withHotbarSlot(LocalPlayer player, int slot, Runnable action) {
      if (slot < 0 || slot > 8) {
         return;
      }

      int previous = player.getInventory().getSelectedSlot();
      if (slot == previous) {
         action.run();
         return;
      }

      player.getInventory().setSelectedSlot(slot);

      try {
         action.run();
      } finally {
         player.getInventory().setSelectedSlot(previous);
      }
   }

   /**
    * Finds a matching hotbar item, claims the main hand for the module that is running, and runs the action with
    * that item selected. Returns false when there is no such item or a more important module holds the hand.
    */
   public static boolean withHotbarItem(LocalPlayer player, Predicate<ItemStack> filter, Runnable action) {
      int slot = findHotbar(player, filter);
      if (slot < 0 || !Hands.claimMainHand()) {
         return false;
      }

      withHotbarSlot(player, slot, action);
      return true;
   }

   public static boolean inventoryOpenOrClosed(LocalPlayer player) {
      return player.containerMenu == player.inventoryMenu;
   }

   public static int find(LocalPlayer player, Predicate<ItemStack> match, boolean hotbarOnly) {
      int limit = hotbarOnly ? 9 : 36;

      for (int i = 0; i < limit; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (!stack.isEmpty() && match.test(stack)) {
            return i;
         }
      }

      return -1;
   }

   public static void swapToOffhand(Minecraft client, LocalPlayer player, int index) {
      if (client.gameMode != null && Hands.claimOffhand()) {
         int container = player.inventoryMenu.containerId;
         if (index < 9) {
            client.gameMode.handleContainerInput(container, 45, index, ContainerInput.SWAP, player);
         } else {
            int menuSlot = menuSlot(index);
            client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(container, 45, 0, ContainerInput.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty()) {
               client.gameMode.handleContainerInput(container, menuSlot, 0, ContainerInput.PICKUP, player);
            }
         }
      }
   }
}
