package gemini.module.modules.misc;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.util.SpCommands;

/** Runs a list of commands when switched on (bind it to a key). Lines starting with / run as world commands. */
public final class Macro extends Module {
   private final ListSetting lines;

   public Macro(int slot) {
      super("Macro " + slot, "Run a list of commands with one key: /commands, .gemini commands or chat.", Category.CLIENT);
      this.lines = this.addSetting(
         new ListSetting(
            "Commands",
            "One per line: /time set day runs in your world, .speed toggles a module, anything else is sent as chat",
            ListSetting.Kind.TEXT,
            slot == 1 ? new String[]{"/time set day", "/weather clear"} : new String[0]
         )
      );
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      int ran = 0;
      for (String line : this.lines.get()) {
         String command = line.trim();
         if (command.isEmpty()) {
            continue;
         }

         if (command.startsWith("/")) {
            SpCommands.run(command.substring(1));
         } else if ((command.startsWith(".") || command.startsWith("#")) && Gemini.getInstance() != null) {
            Gemini.getInstance().commands().handle(command);
         } else {
            CommandManager.sendRaw(command);
         }

         ran++;
      }

      CommandManager.feedback(ran == 0 ? "§b" + this.getName() + " §7has no commands yet. Add some in its settings." : "§b" + this.getName() + " §7ran " + ran + (ran == 1 ? " command." : " commands."));
      this.setEnabled(false, false);
   }
}
