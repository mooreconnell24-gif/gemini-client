package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;

public final class RegenModule extends Module {
   private final NumberSetting amount = this.addSetting(new NumberSetting("Amount", "Health restored each time (half-hearts)", 1.0, 0.5, 10.0, 0.5));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Interval", "Ticks between heals", 10.0, 1.0, 40.0, 1.0));
   private int ticks;

   public RegenModule() {
      super("Regen", "Heals you a set amount at a regular interval.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      if (++this.ticks >= this.interval.intValue()) {
         this.ticks = 0;
         float heal = this.amount.floatValue();
         AttributeBoost.onServer(serverPlayer -> {
            if (serverPlayer.isAlive() && serverPlayer.getHealth() < serverPlayer.getMaxHealth()) {
               serverPlayer.heal(heal);
            }
         });
      }
   }
}
