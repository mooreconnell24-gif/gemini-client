package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;

public final class PortalMenu extends Module {
   public static PortalMenu instance;

   public PortalMenu() {
      super("PortalMenu", "Allow opening GUIs and chat inside Nether portals.", Category.PLAYER);
      instance = this;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.gui.screen() == null && client.player.portalProcess != null && client.player.portalProcess.isInsidePortalThisTick()) {
         while (client.options.keyInventory.consumeClick()) {
            client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
         }
         while (client.options.keyChat.consumeClick()) {
            client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
         }
      }
   }
}
