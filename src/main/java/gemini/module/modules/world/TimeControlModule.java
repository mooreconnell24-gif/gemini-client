package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;

public final class TimeControlModule extends Module {
   private final ModeSetting preset = this.addSetting(new ModeSetting("Time", "Time of day to set: Day (1000), Noon (6000), Night (13000), Midnight (18000) or Custom", "Day", "Day", "Noon", "Night", "Midnight", "Custom"));
   private final NumberSetting ticks = this.addSetting(
      new NumberSetting("Ticks", "Time of day in ticks for Custom (0 = sunrise, 6000 = noon, 18000 = midnight)", 6000.0, 0.0, 24000.0, 250.0).showIf(() -> this.preset.is("Custom"))
   );
   private final BooleanSetting lock = this.addSetting(new BooleanSetting("Lock", "Stay on and set the time again every 2 seconds so the clock barely moves; off sets it once and turns off", false));
   private int counter;

   public TimeControlModule() {
      super("Time", "Sets the world's time of day, like /time set; turning the module off does not undo it.", Category.WORLD);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public boolean showInArrayList() {
      return this.lock.get();
   }

   private String command() {
      String var1 = this.preset.get();

      return switch (var1) {
         case "Noon" -> "time set noon";
         case "Night" -> "time set night";
         case "Midnight" -> "time set midnight";
         case "Custom" -> "time set " + this.ticks.intValue();
         default -> "time set day";
      };
   }

   @Override
   public void onEnable() {
      this.counter = 0;
      if (SpCommands.run(this.command())) {
         CommandManager.feedback("§bGemini §7time set to §f" + this.preset.get());
      }

      if (!this.lock.get()) {
         this.setEnabled(false, false);
      }
   }

   @Override
   public void onTick() {
      if (!this.lock.get()) {
         // Lock was switched off while running: there is nothing left to keep up.
         this.setEnabled(false, false);
      } else if (++this.counter % 40 == 0) {
         SpCommands.run(this.command());
      }
   }
}
