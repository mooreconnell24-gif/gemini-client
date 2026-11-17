package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.InfoText;
import java.util.ArrayList;
import java.util.List;

public final class RegenModule extends Module {
   private final NumberSetting amount = this.addSetting(new NumberSetting("Amount", "Health restored each time, in half-hearts", 1.0, 0.5, 20.0, 0.5));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Interval", "Ticks between heals (20 ticks are one second)", 10.0, 1.0, 200.0, 1.0));
   private final NumberSetting below = this.addSetting(
      new NumberSetting("Below Health", "Only heal at or below this health; 20 heals whenever you are hurt", 20.0, 1.0, 20.0, 1.0)
   );
   private final InfoText infoText = new InfoText();
   private int ticks;

   public RegenModule() {
      super("Regen", "Heals you a set amount at a regular interval, always or only when your health is low.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Auto Heal");
      names.add("AutoHeal");
      return names;
   }

   @Override
   public void onEnable() {
      this.ticks = 0;
   }

   @Override
   public void onTick() {
      if (++this.ticks >= this.interval.intValue()) {
         this.ticks = 0;
         float heal = this.amount.floatValue();
         float threshold = this.below.floatValue();
         AttributeBoost.onServer(serverPlayer -> {
            if (serverPlayer.isAlive() && serverPlayer.getHealth() < serverPlayer.getMaxHealth() && serverPlayer.getHealth() <= threshold) {
               serverPlayer.heal(heal);
            }
         });
      }
   }

   @Override
   public String info() {
      return this.infoText.fixed(this.amount.get(), 1);
   }
}
