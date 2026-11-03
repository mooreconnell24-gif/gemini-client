package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;

public final class Gamemode extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Gamemode to apply", "Creative", "Survival", "Creative", "Adventure", "Spectator")
   );

   public Gamemode() {
      super("Gamemode", "Switch survival, creative, adventure or spectator.", Category.PLAYER);
      this.addSetting(new ActionSetting("Apply", "Switch to the selected gamemode", this::apply));
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
   public void onEnable() {
      this.apply();
      this.setEnabled(false, false);
   }

   private void apply() {
      if (SpCommands.gamemode(this.mode.get())) {
         CommandManager.feedback("§bGamemode §7" + this.mode.get().toLowerCase(Locale.ROOT) + ".");
      } else {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
