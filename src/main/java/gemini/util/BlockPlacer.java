package gemini.util;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPlacer {
   private static final Direction[] ORDER = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

   private BlockPlacer() {
   }

   public static boolean replaceable(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced();
   }

   public static boolean occupied(Level level, BlockPos pos) {
      List<Entity> inside = level.getEntities((Entity)null, new AABB(pos), entity -> entity.blocksBuilding && !entity.isSpectator());
      return !inside.isEmpty();
   }

   public static BlockHitResult findPlacement(Level level, BlockPos target, boolean allowAir) {
      for (Direction direction : ORDER) {
         BlockPos neighbour = target.relative(direction);
         BlockState state = level.getBlockState(neighbour);
         if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, neighbour).isEmpty() && state.getFluidState().isEmpty()) {
            Direction face = direction.getOpposite();
            Vec3 location = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            return new BlockHitResult(location, face, neighbour, false);
         }
      }

      return allowAir ? new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false) : null;
   }

   public static boolean solidFiller(ItemStack stack) {
      if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         return !(block instanceof EntityBlock) && !(block instanceof FallingBlock) && block.defaultBlockState().isSolidRender();
      } else {
         return false;
      }
   }

   public static Predicate<ItemStack> blockItem(Block block) {
      return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() == block;
   }

   public static int pickSlot(LocalPlayer player, Predicate<ItemStack> filter) {
      int selected = player.getInventory().getSelectedSlot();
      ItemStack held = player.getInventory().getItem(selected);
      return !held.isEmpty() && filter.test(held) ? selected : Inventories.findHotbar(player, filter);
   }

   public static boolean inReach(LocalPlayer player, BlockPos pos, double reach) {
      Vec3 eye = player.getEyePosition();
      return Vec3.atCenterOf(pos).distanceToSqr(eye) <= reach * reach;
   }

   public static boolean place(Minecraft client, BlockPos target, Predicate<ItemStack> filter, boolean swing, boolean restoreSlot, boolean allowAir) {
      return place(client, target, filter, swing, restoreSlot, allowAir, true);
   }

   public static boolean place(
      Minecraft client, BlockPos target, Predicate<ItemStack> filter, boolean swing, boolean restoreSlot, boolean allowAir, boolean checkEntities
   ) {
      LocalPlayer player = client.player;
      if (player == null || client.level == null || client.gameMode == null) {
         return false;
      } else if (!replaceable(client.level, target)) {
         return false;
      } else {
         int slot = pickSlot(player, filter);
         if (slot < 0) {
            return false;
         } else {
            BlockHitResult hit = findPlacement(client.level, target, allowAir);
            if (hit != null && (!checkEntities || !occupied(client.level, target)) && Hands.claimMainHand()) {
               int previous = player.getInventory().getSelectedSlot();
               if (slot != previous) {
                  player.getInventory().setSelectedSlot(slot);
               }

               InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
               if (swing) {
                  Swing.interact(player, InteractionHand.MAIN_HAND);
               }

               if (restoreSlot && slot != previous) {
                  player.getInventory().setSelectedSlot(previous);
               }

               return result.consumesAction();
            } else {
               return false;
            }
         }
      }
   }
}
