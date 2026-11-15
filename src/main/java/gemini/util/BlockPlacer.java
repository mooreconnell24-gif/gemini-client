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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

   /** Whether a neighbour can be placed against: a solid, non-fluid block with a collision shape. */
   public static boolean solidSupport(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
   }

   /**
    * Like {@link #findPlacement} but only accepts a neighbour face the eye is in front of, nearest first, so a look
    * ray toward the face can actually reach it. Returns null when no visible face is in {@code reach}.
    */
   public static BlockHitResult findVisiblePlacement(Level level, Vec3 eye, BlockPos target, double reach) {
      BlockHitResult best = null;
      double bestDistance = reach * reach;
      for (Direction direction : ORDER) {
         BlockPos neighbour = target.relative(direction);
         if (!solidSupport(level, neighbour)) {
            continue;
         }

         Direction face = direction.getOpposite();
         double fx = neighbour.getX() + 0.5 + face.getStepX() * 0.5;
         double fy = neighbour.getY() + 0.5 + face.getStepY() * 0.5;
         double fz = neighbour.getZ() + 0.5 + face.getStepZ() * 0.5;
         // In front of the face's plane, i.e. on the side the face points to.
         double facing = (eye.x - fx) * face.getStepX() + (eye.y - fy) * face.getStepY() + (eye.z - fz) * face.getStepZ();
         double distance = eye.distanceToSqr(fx, fy, fz);
         if (facing > 1.0E-3 && distance <= bestDistance) {
            bestDistance = distance;
            best = new BlockHitResult(new Vec3(fx, fy, fz), face, neighbour, false);
         }
      }

      return best;
   }

   /** The unit look vector for a yaw and pitch in degrees, the same maths as {@code Entity.calculateViewVector}. */
   public static Vec3 lookVector(float yaw, float pitch) {
      float pitchRad = pitch * (float)(Math.PI / 180.0);
      float yawRad = -yaw * (float)(Math.PI / 180.0);
      float cosYaw = net.minecraft.util.Mth.cos(yawRad);
      float sinYaw = net.minecraft.util.Mth.sin(yawRad);
      float cosPitch = net.minecraft.util.Mth.cos(pitchRad);
      float sinPitch = net.minecraft.util.Mth.sin(pitchRad);
      return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
   }

   /**
    * Casts the look ray for {@code yaw}/{@code pitch} from the player's eye through the world (block outlines, no
    * fluids, as the crosshair does) and returns the hit only if it lands on {@code intended}'s block and face. The
    * returned hit carries the exact point the ray met the face, which is what a real click would send. Null means
    * that rotation would click something else, or nothing.
    */
   public static BlockHitResult verifyLook(Level level, LocalPlayer player, float yaw, float pitch, double reach, BlockHitResult intended) {
      if (level == null || player == null || intended == null) {
         return null;
      }

      Vec3 eye = player.getEyePosition();
      Vec3 look = lookVector(yaw, pitch);
      Vec3 end = new Vec3(eye.x + look.x * reach, eye.y + look.y * reach, eye.z + look.z * reach);
      BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
      return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(intended.getBlockPos()) && hit.getDirection() == intended.getDirection()
         ? hit
         : null;
   }

   /**
    * Places against a hit that was already chosen (and usually verified): picks the hotbar block, claims the main
    * hand, clicks, swings. Returns whether the click was consumed.
    */
   public static boolean placeWithHit(
      Minecraft client, BlockPos target, BlockHitResult hit, Predicate<ItemStack> filter, boolean swing, boolean restoreSlot, boolean checkEntities
   ) {
      LocalPlayer player = client.player;
      if (player == null || client.level == null || client.gameMode == null || hit == null || !replaceable(client.level, target)) {
         return false;
      }

      int slot = pickSlot(player, filter);
      if (slot < 0 || checkEntities && occupied(client.level, target) || !Hands.claimMainHand()) {
         return false;
      }

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
