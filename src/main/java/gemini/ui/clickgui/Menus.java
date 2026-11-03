package gemini.ui.clickgui;

import gemini.Gemini;
import gemini.module.modules.ClickGuiModule;
import net.minecraft.client.gui.screens.Screen;

/** Opens the menu in the layout picked in the ClickGUI module, and recognises either layout. */
public final class Menus {
   private Menus() {
   }

   public static Screen create() {
      ClickGuiModule style = Gemini.getInstance() == null ? null : Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
      return style != null && style.panelLayout() ? new ClickGuiScreen() : new WindowGuiScreen();
   }

   public static boolean isMenu(Screen screen) {
      return screen instanceof ClickGuiScreen || screen instanceof WindowGuiScreen;
   }
}
