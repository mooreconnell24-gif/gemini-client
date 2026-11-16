package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.SpCommands;

public final class WeatherControlModule extends Module {
   private final ModeSetting weather = this.addSetting(new ModeSetting("Weather", "Weather to set", "Clear", "Clear", "Rain", "Thunder"));
   private final BooleanSetting lock = this.addSetting(new BooleanSetting("Lock", "Stay on and set this weather again every 5 seconds; off sets it once and switches off", false));
   private int counter;

   public WeatherControlModule() {
      super("Weather", "Set the world's weather, like /weather. This is a lasting change to the world: switching the module off does not undo it.", Category.WORLD);
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
      String var1 = this.weather.get();

      return switch (var1) {
         case "Rain" -> "weather rain";
         case "Thunder" -> "weather thunder";
         default -> "weather clear";
      };
   }

   @Override
   public void onEnable() {
      this.counter = 0;
      if (SpCommands.run(this.command())) {
         CommandManager.feedback("§bGemini §7weather set to §f" + this.weather.get());
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
      } else if (++this.counter % 100 == 0) {
         SpCommands.run(this.command());
      }
   }
}
