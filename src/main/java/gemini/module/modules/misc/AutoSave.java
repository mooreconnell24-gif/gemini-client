package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.Locale;

public final class AutoSave extends Module {
   private final NumberSetting interval = this.addSetting(new NumberSetting("Interval", "Minutes between saves", 5.0, 1.0, 60.0, 1.0).onChange(this::restart));
   private final BooleanSetting flush = this.addSetting(
      new BooleanSetting("Flush", "Write everything to disk immediately (a short freeze on big worlds)", false)
   );
   private final BooleanSetting notify = this.addSetting(new BooleanSetting("Notify", "Say in chat when a save happens", true));
   private int ticks;
   private int saves;

   public AutoSave() {
      super("Auto Save", "Save the world every few minutes.", Category.CLIENT);
      this.addSetting(new ActionSetting("Save Now", "Save the world right away", this::saveNow));
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.restart();
   }

   private void restart() {
      this.ticks = this.interval.intValue() * 1200;
   }

   @Override
   public void onTick() {
      if (--this.ticks <= 0) {
         this.saveNow();
         this.restart();
      }
   }

   private void saveNow() {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.run(this.flush.get() ? "save-all flush" : "save-all")) {
         this.saves++;
         if (this.notify.get()) {
            CommandManager.feedback("§bAuto Save §7world saved.");
         }
      }
   }

   @Override
   public String info() {
      int seconds = Math.max(0, this.ticks) / 20;
      return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
   }
}
