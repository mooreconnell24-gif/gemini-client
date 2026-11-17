package gemini.module.modules.player;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Damage;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats from the hotbar for two reasons, hunger and low health. Hunger takes the most filling ordinary food; low
 * health takes a golden apple (a plain one first, an enchanted one when there is no plain one). The meal is held
 * until it is finished and the hotbar slot is put back afterwards.
 */
public final class AutoEatModule extends Module {
   private static final Set<String> BAD_FOOD = Set.of("rotten_flesh", "spider_eye", "pufferfish", "poisonous_potato", "chorus_fruit", "suspicious_stew");
   private final NumberSetting hunger = this.addSetting(new NumberSetting("Hunger", "Eat when your food level is at or below this (20 is full)", 14.0, 1.0, 19.0, 1.0));
   private final ModeSetting goldenApples = this.addSetting(
      new ModeSetting(
         "Golden Apples",
         "Low Health eats one when your health drops; Always also eats them as ordinary food; Never leaves them alone",
         "Low Health",
         "Never",
         "Low Health",
         "Always"
      )
   );
   private final NumberSetting health = this.addSetting(
      new NumberSetting("Health", "Eat a golden apple at or below this health, absorption included", 10.0, 2.0, 19.0, 1.0)
         .showIf(() -> !this.goldenApples.is("Never"))
   );
   private final BooleanSetting badFood = this.addSetting(new BooleanSetting("Bad Food", "Allow rotten flesh and other food with harmful effects", false));
   private final BooleanSetting restore = this.addSetting(new BooleanSetting("Restore Slot", "Switch back to the previous hotbar slot afterwards", true));
   private int previousSlot = -1;
   /** A slot still to be selected again because a busier module held the hand when eating ended. */
   private int restoreSlot = -1;
   private boolean eating;

   public AutoEatModule() {
      super("Auto Eat", "Eats food from your hotbar when you get hungry, and a golden apple when your health is low.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("AutoGapple");
      names.add("AutoSoup");
      names.add("SmartEat");
      return names;
   }

   /** Eating for health competes with combat modules for the hand, so it ranks with them. */
   @Override
   public ActionPriority priority() {
      return ActionPriority.COMBAT;
   }

   @Override
   public void onDisable() {
      this.finish(Minecraft.getInstance());
      this.restoreSlot = -1;
   }

   @Override
   public void onWorldChange() {
      this.previousSlot = -1;
      this.restoreSlot = -1;
      this.finish(Minecraft.getInstance());
   }

   @Override
   public String info() {
      return this.eating ? "Eating" : null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null) {
         return;
      }

      if (this.eating) {
         if (player.isUsingItem() && client.gui.screen() == null && Hands.claimMainHand()) {
            client.options.keyUse.setDown(true);
         } else {
            // Finished, interrupted by a screen, or the hand was taken: never leave the use key held down.
            this.finish(client);
         }
      } else if (this.restoreSlot >= 0) {
         if (Hands.claimMainHand()) {
            player.getInventory().setSelectedSlot(this.restoreSlot);
            this.restoreSlot = -1;
         }
      } else if (client.gui.screen() == null && !player.isUsingItem() && !player.isDeadOrDying()) {
         int slot = this.chooseSlot(player);
         if (slot >= 0 && Hands.claimMainHand()) {
            this.previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            client.options.keyUse.setDown(true);
            this.eating = true;
         }
      }
   }

   /** The hotbar slot to eat from now, or -1 when there is no reason to eat or nothing suitable. */
   private int chooseSlot(LocalPlayer player) {
      if (!this.goldenApples.is("Never") && Damage.health(player) <= this.health.floatValue()) {
         int apple = Inventories.findHotbar(player, stack -> stack.is(Items.GOLDEN_APPLE));
         if (apple < 0) {
            apple = Inventories.findHotbar(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
         }

         if (apple >= 0) {
            return apple;
         }
      }

      if (player.getFoodData().getFoodLevel() > this.hunger.intValue()) {
         return -1;
      }

      int best = -1;
      int bestNutrition = 0;
      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         FoodProperties food = stack.get(DataComponents.FOOD);
         if (food != null && this.allowedForHunger(stack) && player.canEat(food.canAlwaysEat()) && food.nutrition() > bestNutrition) {
            bestNutrition = food.nutrition();
            best = i;
         }
      }

      return best;
   }

   private boolean allowedForHunger(ItemStack stack) {
      String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
      if (!this.badFood.get() && BAD_FOOD.contains(path)) {
         return false;
      }

      return this.goldenApples.is("Always") || !path.contains("golden_apple");
   }

   private void finish(Minecraft client) {
      if (this.eating) {
         this.eating = false;
         client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
         if (this.restore.get() && client.player != null && this.previousSlot >= 0) {
            if (Hands.claimMainHand()) {
               client.player.getInventory().setSelectedSlot(this.previousSlot);
            } else {
               this.restoreSlot = this.previousSlot;
            }
         }

         this.previousSlot = -1;
      }
   }
}
