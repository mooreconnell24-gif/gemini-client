package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

public final class AutoEatModule extends Module {
   private static final Set<String> BAD_FOOD = Set.of("rotten_flesh", "spider_eye", "pufferfish", "poisonous_potato", "chorus_fruit", "suspicious_stew");
   private final NumberSetting hunger = this.addSetting(new NumberSetting("Hunger", "Eat when food level is at or below this", 14.0, 1.0, 19.0, 1.0));
   private final BooleanSetting goldenApples = this.addSetting(new BooleanSetting("Golden Apples", "Allow golden apples as food", false));
   private final BooleanSetting badFood = this.addSetting(new BooleanSetting("Bad Food", "Allow rotten flesh, spider eyes and other harmful food", false));
   private final BooleanSetting restore = this.addSetting(new BooleanSetting("Restore Slot", "Return to the previous hotbar slot afterwards", true));
   private int previousSlot = -1;
   private boolean eating;

   public AutoEatModule() {
      super("AutoEat", "Eat food automatically when hungry.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.finish(Minecraft.getInstance());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null) {
         if (this.eating) {
            if (player.isUsingItem() && client.gui.screen() == null) {
               client.options.keyUse.setDown(true);
            } else {
               this.finish(client);
            }
         } else if (client.gui.screen() == null && !player.isUsingItem() && player.getFoodData().getFoodLevel() <= this.hunger.intValue()) {
            int best = -1;
            int bestNutrition = 0;

            for (int i = 0; i < 9; i++) {
               ItemStack stack = player.getInventory().getItem(i);
               FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
               if (food != null && this.allowed(stack) && player.canEat(food.canAlwaysEat()) && food.nutrition() > bestNutrition) {
                  bestNutrition = food.nutrition();
                  best = i;
               }
            }

            if (best >= 0) {
               this.previousSlot = player.getInventory().getSelectedSlot();
               player.getInventory().setSelectedSlot(best);
               client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
               client.options.keyUse.setDown(true);
               this.eating = true;
            }
         }
      }
   }

   private boolean allowed(ItemStack stack) {
      String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
      return !this.badFood.get() && BAD_FOOD.contains(path) ? false : this.goldenApples.get() || !path.contains("golden_apple");
   }

   private void finish(Minecraft client) {
      if (this.eating) {
         this.eating = false;
         client.options.keyUse.setDown(false);
         if (this.restore.get() && client.player != null && this.previousSlot >= 0) {
            client.player.getInventory().setSelectedSlot(this.previousSlot);
         }

         this.previousSlot = -1;
      }
   }
}
