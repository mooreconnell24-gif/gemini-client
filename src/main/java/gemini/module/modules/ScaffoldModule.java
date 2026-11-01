package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.InventoryUtil;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ScaffoldModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between block placements", 0.0, 0.0, 6.0, 1.0));
   private final NumberSetting expand = this.addSetting(new NumberSetting("Expand", "Extra blocks bridged ahead", 0.0, 0.0, 4.0, 1.0));
   private final ModeSetting sprintMode = this.addSetting(
      new ModeSetting(
         "Sprint",
         "Sprinting bridge style",
         "Godbridge",
         "Telly",
         "Godbridge",
         "Breeze",
         "Eagle",
         "Normal",
         "Legit",
         "Off"
      )
   );
   private final ModeSetting rotate = this.addSetting(
      new ModeSetting(
         "Rotate",
         "Aim mode: Silent packet rotations",
         "Silent",
         "Silent",
         "Godbridge",
         "Strict",
         "Snap",
         "None"
      )
   );
   private final NumberSetting turnSpeed = this.addSetting(
      new NumberSetting("Turn Speed", "Rotation speed in degrees per tick", 95.0, 20.0, 180.0, 5.0).showIf(() -> !this.rotate.is("None"))
   );
   private final ModeSetting towerMode = this.addSetting(
      new ModeSetting("Tower", "Vertical tower motion while holding Jump", "Bypass", "Bypass", "Motion", "Constant", "Jump", "Off")
   );
   private final NumberSetting towerSpeed = this.addSetting(
      new NumberSetting("Tower Motion", "Upward velocity while towering", 0.42, 0.25, 0.6, 0.02).showIf(() -> !this.towerMode.is("Off"))
   );
   private final BooleanSetting keepY = this.addSetting(
      new BooleanSetting("Keep Y", "Lock placement height to starting Y level", false)
   );
   private final BooleanSetting clutchSearch = this.addSetting(
      new BooleanSetting("Clutch Search", "Search up to 3 blocks away for diagonal/air support", true)
   );
   private final BooleanSetting autoRefill = this.addSetting(
      new BooleanSetting("Auto Refill", "Refill hotbar blocks from inventory automatically", true)
   );
   private final BooleanSetting safeWalk = this.addSetting(new BooleanSetting("Safe Walk", "Prevent walking off block edges", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing hand on block placement", true));
   private final BooleanSetting keepSlot = this.addSetting(new BooleanSetting("Restore Slot", "Restore original hotbar slot after placing", false));
   private final BooleanSetting sneakPause = this.addSetting(new BooleanSetting("Pause Sneak", "Pause placement while manually sneaking", false));
   private final BooleanSetting downwards = this.addSetting(new BooleanSetting("Downwards", "Place blocks 1 level lower when sneaking", false));
   private final BooleanSetting renderBlock = this.addSetting(new BooleanSetting("Highlight", "Render 3D box on active scaffold block", true));
   private int cooldown;
   private int startY;
   private int towerTicks;
   private int breezeTick;
   private boolean sneaking;
   private BlockPos lastPlacedPos;

   public ScaffoldModule() {
      super("Scaffold", "Fast block bridging with clutch search.", Category.WORLD);
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      this.startY = player == null ? 0 : Mth.floor(player.getY() - 0.01) - 1;
      this.sneaking = false;
      this.towerTicks = 0;
      this.breezeTick = 0;
      this.lastPlacedPos = null;
   }

   @Override
   public void onDisable() {
      this.releaseSneak();
      this.lastPlacedPos = null;
   }

   @Override
   public String info() {
      Minecraft client = Minecraft.getInstance();
      int blocks = client.player == null ? 0 : countBlocks(client.player);
      boolean towering = !this.towerMode.is("Off") && client.options.keyJump.isDown() && !MoveUtil.hasInput(client.player);
      return (towering ? "Tower" : this.sprintMode.get()) + " · " + blocks;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null && client.gui.screen() == null && !player.isSpectator()) {
         if (this.autoRefill.get() && findBlockSlot(player) == -1) {
            refillHotbarBlocks(client, player);
         }

         int slot = findBlockSlot(player);
         if (slot == -1) {
            this.releaseSneak();
            return;
         }

         if (player.onGround() && !this.keepY.get() && !this.sprintMode.is("Telly")) {
            this.startY = Mth.floor(player.getY() - 0.01) - 1;
         } else if (player.getY() < this.startY) {
            this.startY = Mth.floor(player.getY() - 0.01) - 1;
         }

         boolean towering = !this.towerMode.is("Off") && client.options.keyJump.isDown() && (!MoveUtil.hasInput(player) || !this.keepY.get() && !this.sprintMode.is("Telly"));
         if (!towering) {
            this.towerTicks = 0;
         }

         this.applySprintControl(client, player, towering);
         if ((this.safeWalk.get() || this.sprintMode.is("Eagle")) && !towering) {
            this.holdEdge(client, player);
         } else {
            this.releaseSneak();
         }

         if ((this.keepY.get() || this.sprintMode.is("Telly")) && MoveUtil.hasInput(player) && player.onGround() && !client.options.keyJump.isDown()) {
            player.jumpFromGround();
         }

         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }

         if (this.sneakPause.get() && MoveUtil.physicalKeyDown(client.options.keyShift) && !this.downwards.get()) {
            return;
         }

         int targetY;
         if (towering) {
            targetY = Mth.floor(player.getY() - 0.05) - 1;
         } else if (this.downwards.get() && MoveUtil.physicalKeyDown(client.options.keyShift)) {
            targetY = Mth.floor(player.getY() - 0.01) - 2;
            this.startY = targetY;
         } else if (this.keepY.get() || this.sprintMode.is("Telly")) {
            targetY = this.startY;
         } else {
            targetY = Mth.floor(player.getY() - 0.01) - 1;
         }

         BlockPos feet = new BlockPos(Mth.floor(player.getX()), targetY, Mth.floor(player.getZ()));
         List<BlockPos> targets = new ArrayList<>();
         targets.add(feet);

         Vec3 moveDir = MoveUtil.direction(player);
         if (moveDir != null && !towering) {
            BlockPos aheadHalf = new BlockPos(Mth.floor(player.getX() + moveDir.x * 0.45), targetY, Mth.floor(player.getZ() + moveDir.z * 0.45));
            if (!targets.contains(aheadHalf)) {
               targets.add(aheadHalf);
            }

            int exp = this.expand.intValue();
            for (int i = 1; i <= exp; i++) {
               BlockPos expPos = new BlockPos(Mth.floor(player.getX() + moveDir.x * i), targetY, Mth.floor(player.getZ() + moveDir.z * i));
               if (!targets.contains(expPos)) {
                  targets.add(expPos);
               }
            }
         }

         PlacementPlan plan = null;
         for (BlockPos candidate : targets) {
            if (replaceable(client.level, candidate)) {
               plan = findPlacementWithClutch(client.level, player, candidate, this.clutchSearch.get() ? 3 : 1);
               if (plan != null) {
                  break;
               }
            }
         }

         if (plan == null && !this.rotate.is("None") && MoveUtil.hasInput(player)) {
            float[] idleRot = this.computeBridgeRotation(player, null);
            RotationManager.request(player, idleRot[0], idleRot[1], this.turnSpeed.floatValue(), !this.rotate.is("Snap"), 5, 10.0F);
         }

         if (plan != null) {
            if (!this.rotate.is("None")) {
               boolean quiet = !this.rotate.is("Snap");
               float[] placeRot = this.computeBridgeRotation(player, plan.hit);
               boolean ready = RotationManager.request(player, placeRot[0], placeRot[1], this.turnSpeed.floatValue(), quiet, 6, 16.0F);
               if (!ready && this.rotate.is("Strict")) {
                  return;
               }
            }

            if (this.sprintMode.is("Legit") && player.isSprinting()) {
               player.setSprinting(false);
            }

            int previous = player.getInventory().getSelectedSlot();
            if (previous != slot) {
               player.getInventory().setSelectedSlot(slot);
            }

            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, plan.hit);
            if (this.swing.get()) {
               player.swing(InteractionHand.MAIN_HAND);
            }

            if (this.keepSlot.get() && previous != slot) {
               player.getInventory().setSelectedSlot(previous);
            }

            this.lastPlacedPos = plan.placePos.immutable();
            if (towering) {
               this.applyTowerMotion(player, plan.placePos);
            }

            this.cooldown = this.delay.intValue();
         }
      } else {
         this.releaseSneak();
      }
   }

   private void applySprintControl(Minecraft client, LocalPlayer player, boolean towering) {
      if (MoveUtil.hasInput(player) && !MoveUtil.hasRecentSetback() && !towering) {
         String mode = this.sprintMode.get();
         switch (mode) {
            case "Off":
            case "Eagle":
               player.setSprinting(false);
               break;
            case "Breeze":
               player.setSprinting(true);
               this.breezeTick++;
               if (player.onGround() && this.breezeTick % 3 == 0) {
                  Vec3 motion = player.getDeltaMovement();
                  double side = (this.breezeTick / 3 % 2 == 0 ? 1.0 : -1.0) * 0.042;
                  double strafeRad = Math.toRadians(movementYaw(player) + 90.0F);
                  player.setDeltaMovement(motion.x - Math.sin(strafeRad) * side, motion.y, motion.z + Math.cos(strafeRad) * side);
               }
               break;
            case "Telly":
            case "Godbridge":
            case "Normal":
               if (!player.isCrouching()) {
                  player.setSprinting(true);
               }
               break;
         }
      }
   }

   private float[] computeBridgeRotation(LocalPlayer player, BlockHitResult hit) {
      if (this.rotate.is("Godbridge")) {
         float moveYaw = movementYaw(player);
         float pitch = hit != null ? Mth.clamp(RotationManager.toward(player, hit.getLocation())[1], 74.0F, 79.5F) : 75.6F;
         return new float[]{Mth.wrapDegrees(moveYaw + 135.0F), pitch};
      } else if (hit != null) {
         float[] exact = RotationManager.toward(player, hit.getLocation());
         if (this.rotate.is("Strict")) {
            return exact;
         }
         float moveYaw = MoveUtil.hasInput(player) ? Mth.wrapDegrees(movementYaw(player) + 180.0F) : exact[0];
         float blendedYaw = moveYaw + Mth.wrapDegrees(exact[0] - moveYaw) * 0.65F;
         return new float[]{Mth.wrapDegrees(blendedYaw), Mth.clamp(exact[1], 70.0F, 88.5F)};
      } else {
         float moveYaw = movementYaw(player);
         return new float[]{Mth.wrapDegrees(moveYaw + 180.0F), 79.5F};
      }
   }

   private static float movementYaw(LocalPlayer player) {
      Vec3 dir = MoveUtil.direction(player);
      return dir != null ? (float)(Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F : player.getYRot();
   }

   private void applyTowerMotion(LocalPlayer player, BlockPos target) {
      this.towerTicks++;
      Vec3 motion = player.getDeltaMovement();
      double horizScale = MoveUtil.hasInput(player) ? 0.65 : 0.15;
      if (this.towerMode.is("Bypass")) {
         if (this.towerTicks % 8 == 0) {
            player.setDeltaMovement(motion.x * horizScale, -0.0784, motion.z * horizScale);
            this.towerTicks = 0;
         } else {
            player.setDeltaMovement(motion.x * horizScale, this.towerSpeed.get(), motion.z * horizScale);
         }
      } else if (this.towerMode.is("Motion")) {
         if (player.getY() - (target.getY() + 1.0) > -0.15) {
            player.setDeltaMovement(motion.x * horizScale, this.towerSpeed.get(), motion.z * horizScale);
         }
      } else if (this.towerMode.is("Constant")) {
         player.setDeltaMovement(motion.x * horizScale, this.towerSpeed.get(), motion.z * horizScale);
      } else if (this.towerMode.is("Jump") && player.onGround()) {
         player.jumpFromGround();
      }
   }

   private void holdEdge(Minecraft client, LocalPlayer player) {
      if (!player.onGround()) {
         this.releaseSneak();
      } else {
         Level level = client.level;
         BlockPos belowCenter = new BlockPos(Mth.floor(player.getX()), Mth.floor(player.getY() - 0.05) - 1, Mth.floor(player.getZ()));
         boolean overAir = replaceable(level, belowCenter);
         if (overAir) {
            this.sneaking = true;
            client.options.keyShift.setDown(true);
         } else {
            this.releaseSneak();
         }
      }
   }

   private void releaseSneak() {
      if (this.sneaking) {
         this.sneaking = false;
         Minecraft client = Minecraft.getInstance();
         client.options.keyShift.setDown(MoveUtil.physicalKeyDown(client.options.keyShift));
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.renderBlock.get() && this.lastPlacedPos != null) {
         Render3D.block(this.lastPlacedPos, -6467875, true, true, 1.6F, 45, false);
      }
   }

   private static boolean replaceable(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced();
   }

   private static boolean solidSupport(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
   }

   private static PlacementPlan findPlacementWithClutch(Level level, LocalPlayer player, BlockPos goal, int maxRadius) {
      BlockHitResult direct = findDirectHit(level, player, goal);
      if (direct != null) {
         return new PlacementPlan(goal, direct);
      }

      if (maxRadius <= 1) {
         return null;
      }

      List<BlockPos> candidates = new ArrayList<>();
      for (int dx = -maxRadius; dx <= maxRadius; dx++) {
         for (int dy = -2; dy <= 0; dy++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
               if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= maxRadius) {
                  BlockPos pos = goal.offset(dx, dy, dz);
                  if (replaceable(level, pos) && findDirectHit(level, player, pos) != null) {
                     candidates.add(pos);
                  }
               }
            }
         }
      }

      candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(goal)));
      for (BlockPos bridgeStep : candidates) {
         BlockHitResult stepHit = findDirectHit(level, player, bridgeStep);
         if (stepHit != null) {
            return new PlacementPlan(bridgeStep, stepHit);
         }
      }

      return null;
   }

   private static BlockHitResult findDirectHit(Level level, LocalPlayer player, BlockPos target) {
      Direction[] order = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
      Vec3 eye = player.getEyePosition();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction direction : order) {
         BlockPos neighbour = target.relative(direction);
         if (solidSupport(level, neighbour)) {
            Direction face = direction.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            double dist = eye.distanceToSqr(hitVec);
            if (dist <= 22.5 && dist < bestDist) {
               bestDist = dist;
               best = new BlockHitResult(hitVec, face, neighbour, false);
            }
         }
      }

      return best;
   }

   private static void refillHotbarBlocks(Minecraft client, LocalPlayer player) {
      for (int i = 9; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (usable(stack)) {
            InventoryUtil.moveToHotbar(client, player, i, player.getInventory().getSelectedSlot());
            return;
         }
      }
   }

   private static int countBlocks(LocalPlayer player) {
      int total = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (usable(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private static int findBlockSlot(LocalPlayer player) {
      int selected = player.getInventory().getSelectedSlot();
      if (usable(player.getInventory().getItem(selected))) {
         return selected;
      } else {
         int bestSlot = -1;
         int bestCount = 0;
         for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (usable(stack) && stack.getCount() > bestCount) {
               bestCount = stack.getCount();
               bestSlot = i;
            }
         }

         return bestSlot;
      }
   }

   private static boolean usable(ItemStack stack) {
      if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         return !(block instanceof EntityBlock)
            && !(block instanceof FallingBlock)
            && !(block instanceof SlabBlock)
            && !(block instanceof StairBlock)
            && block.defaultBlockState().isSolidRender();
      } else {
         return false;
      }
   }

   private record PlacementPlan(BlockPos placePos, BlockHitResult hit) {
   }
}
