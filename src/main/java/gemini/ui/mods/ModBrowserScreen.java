package gemini.ui.mods;

import gemini.ui.clickgui.WindowGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Entry point of the Modrinth browser. The browser is the "Mods" page of the window menu, so it looks and behaves
 * like the rest of the dashboard; it is a normal screen over the game that never pauses or leaves the world.
 */
public final class ModBrowserScreen {
   private ModBrowserScreen() {
   }

   /** Opens the browser; {@code parent} is shown again when it closes (null closes to the game). */
   public static void open(Screen parent) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.execute(() -> minecraft.gui.setScreen(create(parent)));
   }

   public static Screen create(Screen parent) {
      return new WindowGuiScreen(WindowGuiScreen.Page.MODS, parent);
   }
}
