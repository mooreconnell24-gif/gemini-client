package gemini.module.modules.combat;

import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.Targets;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the combat modules (formerly statics on the module container classes). */
public final class CombatHelpers {
   private CombatHelpers() {
   }

   static boolean click(Minecraft client, LocalPlayer player, BlockPos pos, Direction face) {
      Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
      return client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)).consumesAction();
   }

   static boolean armorDamaged(LocalPlayer player, int percent) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = player.getItemBySlot(slot);
         if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
            double left = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (left < percent) {
               return true;
            }
         }
      }

      return false;
   }

   static Predicate<ItemStack> obsidianOrResistant() {
      return stack -> stack.getItem() instanceof BlockItem bi
         && (bi.getBlock() == Blocks.OBSIDIAN || bi.getBlock() == Blocks.CRYING_OBSIDIAN || bi.getBlock() == Blocks.ENDER_CHEST);
   }
}
