package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;

public final class TimerModule extends Module {
   private final NumberSetting rate = this.addSetting(new NumberSetting("Rate", "Game ticks per second (20 is normal)", 40.0, 5.0, 100.0, 1.0));
   private int applied = -1;

   public TimerModule() {
      super("Timer", "Speed up or slow down the game clock.", Category.WORLD);
   }

   @Override
   public void onEnable() {
      this.applied = -1;
   }

   @Override
   public void onDisable() {
      this.applied = -1;
      SpCommands.run("tick rate 20");
   }

   @Override
   public void onTick() {
      if (this.applied != this.rate.intValue()) {
         this.applied = this.rate.intValue();
         SpCommands.run("tick rate " + this.applied);
      }
   }
}
