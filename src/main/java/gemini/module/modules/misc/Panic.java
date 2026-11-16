package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;

public final class Panic extends Module {
   public Panic() {
      super("Panic", "Turns off every module except the HUD and menu settings, stops Baritone and closes the Gemini menu.", Category.CLIENT);
   }

   @Override
   public boolean restoresEnabledState() {
      return false;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      Gemini client = Gemini.getInstance();
      if (client != null) {
         client.modules().panic();
      }

      CommandManager.feedback("§bPanic §7all modules disabled.");
   }
}
