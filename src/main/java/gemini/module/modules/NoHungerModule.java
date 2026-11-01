package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.util.AttributeBoost;

public final class NoHungerModule extends Module {
   public NoHungerModule() {
      super("NoHunger", "Keep your hunger and saturation full.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      AttributeBoost.onServer(serverPlayer -> {
         if (serverPlayer.getFoodData().getFoodLevel() < 20 || serverPlayer.getFoodData().getSaturationLevel() < 20.0F) {
            serverPlayer.getFoodData().setFoodLevel(20);
            serverPlayer.getFoodData().setSaturation(20.0F);
         }
      });
   }
}
