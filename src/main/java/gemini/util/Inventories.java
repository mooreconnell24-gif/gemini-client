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

   public static int menuSlot(int inventoryIndex) {
      return InventorySlots.toMenuSlot(inventoryIndex);
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
