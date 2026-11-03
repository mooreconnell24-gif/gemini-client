package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class AutoWeapon extends Module {
   private final ModeSetting prefer = this.addSetting(
      new ModeSetting("Prefer", "Preferred weapon type (Best compares DPS)", "Best", "Best", "Sword", "Axe", "Mace", "Trident")
   );
   private final BooleanSetting antiBreak = this.addSetting(new BooleanSetting("Anti Break", "Skip weapons near 0 durability", true));
   private final BooleanSetting keepCurrent = this.addSetting(new BooleanSetting("Keep Current", "Keep current slot if already holding a weapon", false));

   public AutoWeapon() {
      super("Auto Weapon", "Switch to your best hotbar weapon on attack.", Category.COMBAT);
   }

   @Override
   public void onAttack(Entity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && target instanceof LivingEntity) {
         int selected = player.getInventory().getSelectedSlot();
         if (!this.keepCurrent.get() || !isWeapon(player.getInventory().getItem(selected))) {
            int best = -1;
            double bestScore = 0.0;

            for (int slot = 0; slot < 9; slot++) {
               ItemStack stack = player.getInventory().getItem(slot);
               if (isWeapon(stack)
                  && this.matches(stack)
                  && (!this.antiBreak.get() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 2)) {
                  double score = dps(stack);
                  if (score > bestScore) {
                     bestScore = score;
                     best = slot;
                  }
               }
            }

            if (best >= 0 && best != selected) {
               player.getInventory().setSelectedSlot(best);
            }
         }
      }
   }

   private boolean matches(ItemStack stack) {
      String var2 = this.prefer.get();

      return switch (var2) {
         case "Sword" -> stack.is(ItemTags.SWORDS);
         case "Axe" -> stack.is(ItemTags.AXES);
         case "Mace" -> stack.is(Items.MACE);
         case "Trident" -> stack.is(Items.TRIDENT);
         default -> true;
      };
   }

   static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
   }

   static double dps(ItemStack stack) {
      ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
      double[] sums = new double[]{0.0, 0.0};
      modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
         if (attribute.value() == Attributes.ATTACK_DAMAGE.value()) {
            sums[0] += modifier.amount();
         } else if (attribute.value() == Attributes.ATTACK_SPEED.value()) {
            sums[1] += modifier.amount();
         }
      });
      return (1.0 + sums[0]) * Math.max(0.5, 4.0 + sums[1]);
   }
}
