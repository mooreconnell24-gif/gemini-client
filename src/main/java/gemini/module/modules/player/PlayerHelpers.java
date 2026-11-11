package gemini.module.modules.player;

import gemini.util.Hands;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Shared helpers for the player modules (formerly statics on the module container classes). */
public final class PlayerHelpers {
   private PlayerHelpers() {
   }

   static boolean mending(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamageableItem() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP);
   }

   static boolean useHotbarItem(Minecraft client, LocalPlayer player, Item item) {
      if (player.getOffhandItem().is(item)) {
         client.gameMode.useItem(player, InteractionHand.OFF_HAND);
         return true;
      } else {
         int slot = Inventories.findHotbar(player, stack -> stack.is(item));
         if (slot < 0 || !Hands.claimMainHand()) {
            return false;
         } else {
            Inventories.withHotbarSlot(player, slot, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
            return true;
         }
      }
   }
}
