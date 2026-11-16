package gemini.module.modules.player;

import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.RotationManager;
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

   /** Uses the item from the offhand, or from the hotbar with the previous slot selected again afterwards. */
   static boolean useHotbarItem(Minecraft client, LocalPlayer player, Item item) {
      return useHotbarItem(client, player, item, false);
   }

   /**
    * {@link #useHotbarItem(Minecraft, LocalPlayer, Item)}; with {@code aimed} the throw follows this tick's silent
    * rotation ({@link RotationManager#useWithAim}) instead of the camera.
    */
   static boolean useHotbarItem(Minecraft client, LocalPlayer player, Item item, boolean aimed) {
      if (client.gameMode == null) {
         return false;
      }

      InteractionHand hand;
      int slot = -1;
      if (player.getOffhandItem().is(item)) {
         hand = InteractionHand.OFF_HAND;
      } else {
         slot = Inventories.findHotbar(player, stack -> stack.is(item));
         if (slot < 0 || !Hands.claimMainHand()) {
            return false;
         }

         hand = InteractionHand.MAIN_HAND;
      }

      Runnable use = () -> client.gameMode.useItem(player, hand);
      Runnable action = aimed ? () -> RotationManager.useWithAim(player, use) : use;
      if (slot < 0) {
         action.run();
      } else {
         Inventories.withHotbarSlot(player, slot, action);
      }

      return true;
   }
}
