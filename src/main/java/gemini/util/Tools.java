package gemini.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Tool choice and break-time estimates shared by AutoTool-style modules and the pathfinder. */
public final class Tools {
   private Tools() {
   }

   /** The hotbar slot whose item breaks the block fastest (the selected slot when nothing beats it), or -1. */
   public static int bestSlot(LocalPlayer player, BlockState state, boolean spareDamaged) {
      int selected = player.getInventory().getSelectedSlot();
      int best = selected;
      float bestSpeed = player.getInventory().getItem(selected).getDestroySpeed(state);
      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (spareDamaged && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() < 8) {
            continue;
         }

         float speed = stack.getDestroySpeed(state);
         if (speed > bestSpeed + 0.01F) {
            bestSpeed = speed;
            best = i;
         }
      }

      return best;
   }

   /** The best destroy speed any hotbar item gives against this block (1.0 is bare hands). */
   public static float bestSpeed(LocalPlayer player, BlockState state) {
      float best = 1.0F;
      for (int i = 0; i < 9; i++) {
         best = Math.max(best, player.getInventory().getItem(i).getDestroySpeed(state));
      }

      return best;
   }

   /**
    * Roughly how many ticks breaking the block takes with the best hotbar tool. Follows the vanilla formula
    * (speed / hardness / 30 per tick with a matching tool), treating any tool faster than hands as matching,
    * which is right for the common ore/stone/dirt cases the pathfinder cares about. Unbreakable blocks return
    * {@link Integer#MAX_VALUE}.
    */
   public static int breakTicks(LocalPlayer player, BlockGetter level, BlockPos pos, BlockState state) {
      float hardness = state.getDestroySpeed(level, pos);
      if (hardness < 0.0F) {
         return Integer.MAX_VALUE;
      } else if (hardness == 0.0F) {
         return 1;
      }

      float speed = player == null ? 1.0F : bestSpeed(player, state);
      float perTick = speed / hardness / (speed > 1.0F ? 30.0F : 100.0F);
      return perTick <= 0.0F ? Integer.MAX_VALUE : (int)Math.ceil(1.0F / perTick);
   }
}
