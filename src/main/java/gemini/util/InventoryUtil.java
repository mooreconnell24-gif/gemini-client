package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;

public final class InventoryUtil {
   private InventoryUtil() {
   }

   public static void moveToHotbar(Minecraft client, LocalPlayer player, int inventoryIndex, int hotbarSlot) {
      if (client.gameMode == null || inventoryIndex < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
         return;
      }

      int menuSlot = InventorySlots.toMenuSlot(inventoryIndex);
      if (menuSlot < 0) {
         return;
      }

      int container = player.inventoryMenu.containerId;
      client.gameMode.handleContainerInput(container, menuSlot, hotbarSlot, ContainerInput.SWAP, player);
   }
}
