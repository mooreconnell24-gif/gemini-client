package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.event.ActionPriority;
import gemini.util.Hands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SmartEat extends Module {
   private final NumberSetting gappleHealth = this.addSetting(new NumberSetting("Gapple Health", "Eat Golden Apple below this health", 12.0, 4.0, 18.0, 1.0));
   private final NumberSetting hunger = this.addSetting(new NumberSetting("Hunger", "Eat normal food at or below this food level", 16.0, 1.0, 19.0, 1.0));
   private boolean eating;
   private int previousSlot = -1;

   public SmartEat() {
      super("SmartEat", "Eat Golden Apples when low on HP and food when hungry.", Category.PLAYER);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.COMBAT;
   }

   @Override
   public void onDisable() {
      this.stop(Minecraft.getInstance());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         boolean needGap = Damage.health(player) <= this.gappleHealth.floatValue();
         boolean needFood = player.getFoodData().getFoodLevel() <= this.hunger.intValue();
         if (needGap || needFood) {
            // Hunger alone never spends golden apples or eats harmful food.
            int slot = Inventories.findHotbar(player, s -> needGap ? s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.GOLDEN_APPLE) : plainFood(s));
            if (slot < 0 && needGap && needFood) {
               slot = Inventories.findHotbar(player, SmartEat::plainFood);
            }

            if (slot >= 0 && (needGap || player.canEat(false)) && Hands.claimMainHand()) {
               if (!this.eating) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
               }

               player.getInventory().setSelectedSlot(slot);
               client.options.keyUse.setDown(true);
               this.eating = true;
               return;
            }
         }

         this.stop(client);
      }
   }

   private void stop(Minecraft client) {
      if (this.eating) {
         this.eating = false;
         client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
         if (client.player != null && this.previousSlot >= 0 && Hands.claimMainHand()) {
            client.player.getInventory().setSelectedSlot(this.previousSlot);
         }

         this.previousSlot = -1;
      }
   }

   private static final java.util.Set<String> NOT_FOR_HUNGER = java.util.Set.of(
      "golden_apple", "enchanted_golden_apple", "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chorus_fruit", "suspicious_stew"
   );

   private static boolean plainFood(ItemStack stack) {
      return stack.has(DataComponents.FOOD) && !NOT_FOR_HUNGER.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
   }
}
