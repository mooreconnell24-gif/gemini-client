package gemini.ui.clickgui;

import gemini.Gemini;
import gemini.module.modules.misc.ClickGuiModule;
import net.minecraft.client.gui.screens.Screen;

/** Opens the menu in the layout picked in the ClickGUI module, and recognises either layout. */
public final class Menus {
   private Menus() {
   }

   public static Screen create() {
      ClickGuiModule style = Gemini.getInstance() == null ? null : Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
      return style != null && style.panelLayout() ? new ClickGuiScreen() : new WindowGuiScreen();
   }

   /**
    * The screen whose background a menu opened over {@code under} should draw behind its blur while no world is
    * loaded: {@code under} itself, or what the menu underneath was using when one layout is opened from the
    * other (or from the list editor). Null when there is nothing suitable; the menu then shows the panorama.
    */
   static Screen backdropFor(Screen under) {
      if (under instanceof WindowGuiScreen window) {
         return window.backdrop();
      } else if (under instanceof ClickGuiScreen panels) {
         return panels.backdrop();
      } else {
         return under instanceof ListEditorScreen ? null : under;
      }
   }

   public static boolean isMenu(Screen screen) {
      return screen instanceof ClickGuiScreen || screen instanceof WindowGuiScreen;
   }
}
