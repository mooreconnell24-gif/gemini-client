package gemini.module.modules.bot;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;

public final class BaritonePause extends Module {
   public BaritonePause() {
      super("Baritone Pause", "Pause active Baritone pathing and mining.", Category.CLIENT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean wantsBaritonePause() {
      return true;
   }

   @Override
   public void onEnable() {
      BaritoneBridge.pause("manual");
      CommandManager.feedback("§bBaritone §7(" + BaritoneBridge.engineName() + ") paused.");
   }

   @Override
   public void onDisable() {
      BaritoneBridge.resume("manual");
   }

   @Override
   public String info() {
      return "Hold";
   }
}
