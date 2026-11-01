package gemini.util;

public final class InventorySlots {
   private InventorySlots() {
   }

   public static int toMenuSlot(int inventoryIndex) {
      if (inventoryIndex < 0) {
         return -1;
      } else if (inventoryIndex < 9) {
         return 36 + inventoryIndex;
      } else {
         return inventoryIndex < 36 ? 9 + (inventoryIndex - 9) : -1;
      }
   }
}
