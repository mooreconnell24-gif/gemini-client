package gemini.module.modules.bot;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;

public final class BaritoneStatus extends Module {
   public BaritoneStatus() {
      super("Baritone Status", "Show current Baritone engine and task status.", Category.CLIENT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      CommandManager.feedback("§b" + BaritoneBridge.engineName() + " §7status: §f" + BaritoneBridge.statusSummary());
      this.setEnabled(false, false);
   }
}
