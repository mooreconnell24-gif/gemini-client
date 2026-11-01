package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ScaffoldModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between placements", 0.0, 0.0, 6.0, 1.0));
   private final NumberSetting expand = this.addSetting(new NumberSetting("Expand", "Also bridge this many blocks ahead", 0.0, 0.0, 4.0, 1.0));
   private final ModeSetting sprintMode = this.addSetting(
      new ModeSetting(
         "Sprint",
         "Godbridge/Breeze keeps sprint active with bypass rhythm; Legit cancels sprint on place",
         "Godbridge",
         "Telly",
         "Godbridge",
         "Breeze",
         "Normal",
         "Legit",
         "Off"
      )
   );
   private final ModeSetting rotate = this.addSetting(
      new ModeSetting(
         "Rotate",
         "Multi-axis rotation locking: Silent aims in packets so you can look away; Godbridge locks 45-degree pitch/yaw",
         "Silent",
         "Silent",
         "Godbridge",
         "Strict",
         "Snap",
         "None"
      )
   );
   private final NumberSetting turnSpeed = this.addSetting(
      new NumberSetting("Turn Speed", "Rotation speed in degrees per tick (180 = instant)", 85.0, 20.0, 180.0, 5.0).showIf(() -> !this.rotate.is("None"))
   );
   private final ModeSetting towerMode = this.addSetting(
      new ModeSetting("Tower", "Vertical tower motion bypass while holding jump", "Bypass", "Bypass", "Motion", "Jump", "Off")
   );
   private final NumberSetting towerSpeed = this.addSetting(
      new NumberSetting("Tower Motion", "Upward velocity when towering", 0.42, 0.25, 0.6, 0.02).showIf(() -> !this.towerMode.is("Off"))
   );
   private final BooleanSetting keepY = this.addSetting(
      new BooleanSetting("Keep Y", "Stay on the height you started at (auto-jumps while sprint-bridging)", false)
   );
   private final BooleanSetting safeWalk = this.addSetting(new BooleanSetting("Safe Walk", "Sneak at edges so you do not fall off", false));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when placing", true));
   private final BooleanSetting keepSlot = this.addSetting(new BooleanSetting("Restore Slot", "Switch back to your previous slot after placing", true));
   private final BooleanSetting sneakPause = this.addSetting(new BooleanSetting("Pause When Sneaking", "Don't place while crouching", false));
   private final BooleanSetting renderBlock = this.addSetting(new BooleanSetting("Highlight Block", "Render a 3D highlight on the active scaffold block", true));
   private int cooldown;
   private int startY;
   private int towerTicks;
   private int breezeTick;
   private boolean sneaking;
   private BlockPos lastPlacedPos;

   public ScaffoldModule() {
      super("Scaffold", "Place blocks under your feet with multi-axis rotation locking, Godbridge/Breeze sprinting, and tower bypass.", Category.WORLD);
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      this.startY = player == null ? 0 : player.getBlockY();
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
      boolean towering = !this.towerMode.is("Off") && client.options.keyJump.isDown();
      return (towering ? "Tower" : this.sprintMode.get()) + " · " + blocks;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null && client.gui.screen() == null && !player.isSpectator()) {
         this.applySprintControl(player);
         if (this.safeWalk.get()) {
            this.holdEdge(client, player);
         } else {
            this.releaseSneak();
         }

         if (this.keepY.get() && MoveUtil.hasInput(player) && player.onGround() && !client.options.keyJump.isDown()) {
            player.jumpFromGround();
         }

         boolean towering = !this.towerMode.is("Off") && client.options.keyJump.isDown() && !MoveUtil.hasInput(player);
         if (!towering) {
            this.towerTicks = 0;
         }

         if (!this.rotate.is("None") && MoveUtil.hasInput(player)) {
            float[] lockedRot = this.computeBridgeRotation(player, null);
            boolean quiet = !this.rotate.is("Snap");
            RotationManager.request(player, lockedRot[0], lockedRot[1], this.turnSpeed.floatValue(), quiet, 5, 8.0F);
         }

         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (!this.sneakPause.get() || !player.isCrouching() || this.sneaking) {
            double placeY = towering ? player.getY() - 0.1 : (this.keepY.get() ? this.startY - 0.5 : player.getY() - 0.5);
            BlockPos feet = BlockPos.containing(player.getX(), placeY, player.getZ());
            BlockPos target = feet;
            if (this.expand.intValue() > 0 && MoveUtil.hasInput(player) && !towering) {
               Vec3 motion = player.getDeltaMovement();
               double length = Math.hypot(motion.x, motion.z);
               if (length > 0.01) {
                  BlockPos ahead = BlockPos.containing(
                     player.getX() + motion.x / length * this.expand.intValue(), placeY, player.getZ() + motion.z / length * this.expand.intValue()
                  );
                  if (!replaceable(client.level, feet) && replaceable(client.level, ahead)) {
                     target = ahead;
                  }
               }
            }

            if (replaceable(client.level, target)) {
               int slot = findBlockSlot(player);
               if (slot >= 0) {
                  BlockHitResult hit = findPlacement(client.level, target);
                  if (hit != null) {
                     if (!this.rotate.is("None")) {
                        boolean quiet = !this.rotate.is("Snap");
                        float[] placeRot = this.computeBridgeRotation(player, hit);
                        if (!RotationManager.request(player, placeRot[0], placeRot[1], this.turnSpeed.floatValue(), quiet, 5, 12.0F)
                           && this.rotate.is("Strict")) {
                           return;
                        }
                     }

                     if (this.sprintMode.is("Legit") && player.isSprinting()) {
                        player.setSprinting(false);
                     }

                     int previous = player.getInventory().getSelectedSlot();
                     player.getInventory().setSelectedSlot(slot);
                     client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                     if (this.swing.get()) {
                        player.swing(InteractionHand.MAIN_HAND);
                     }

                     if (this.keepSlot.get()) {
                        player.getInventory().setSelectedSlot(previous);
                     }

                     this.lastPlacedPos = target.immutable();
                     if (towering) {
                        this.applyTowerMotion(player, target);
                     }

                     this.cooldown = this.delay.intValue();
                  }
               }
            }
         }
      } else {
         this.releaseSneak();
      }
   }

   private void applySprintControl(LocalPlayer player) {
      if (MoveUtil.hasInput(player) && !MoveUtil.hasRecentSetback()) {
         String mode = this.sprintMode.get();
         switch (mode) {
            case "Off":
               player.setSprinting(false);
               break;
            case "Breeze":
               player.setSprinting(true);
               this.breezeTick++;
               if (player.onGround() && this.breezeTick % 4 == 0) {
                  Vec3 motion = player.getDeltaMovement();
                  double side = (this.breezeTick / 4 % 2 == 0 ? 1.0 : -1.0) * 0.035;
                  double yawRad = Math.toRadians(player.getYRot() + 90.0F);
                  player.setDeltaMovement(motion.x + Math.cos(yawRad) * side, motion.y, motion.z + Math.sin(yawRad) * side);
               }
               break;
            case "Telly":
               player.setSprinting(true);
               if (player.onGround()) {
                  player.jumpFromGround();
               }
               break;
            case "Godbridge":
            case "Normal":
               if (!player.isCrouching()) {
                  player.setSprinting(true);
               }
         }
      }
   }

   private float[] computeBridgeRotation(LocalPlayer player, BlockHitResult hit) {
      if (this.rotate.is("Godbridge")) {
         float moveYaw = movementYaw(player);
         return new float[]{Mth.wrapDegrees(moveYaw + 135.0F), 75.6F};
      } else if (hit != null && this.rotate.is("Strict")) {
         return RotationManager.toward(player, hit.getLocation());
      } else {
         float moveYaw = movementYaw(player);
         float pitch = hit != null ? RotationManager.toward(player, hit.getLocation())[1] : 79.5F;
         return new float[]{Mth.wrapDegrees(moveYaw + 180.0F), Mth.clamp(pitch, 72.0F, 86.0F)};
      }
   }

   private static float movementYaw(LocalPlayer player) {
      Vec3 dir = MoveUtil.direction(player);
      return dir != null ? (float)(Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F : player.getYRot();
   }

   private void applyTowerMotion(LocalPlayer player, BlockPos target) {
      this.towerTicks++;
      Vec3 motion = player.getDeltaMovement();
      if (this.towerMode.is("Bypass")) {
         if (this.towerTicks % 9 == 0) {
            player.setDeltaMovement(motion.x * 0.2, -0.08, motion.z * 0.2);
            this.towerTicks = 0;
         } else {
            player.setDeltaMovement(motion.x * 0.25, this.towerSpeed.get(), motion.z * 0.25);
         }
      } else if (this.towerMode.is("Motion")) {
         if (player.getY() - target.getY() > 0.85) {
            player.setDeltaMovement(motion.x * 0.3, this.towerSpeed.get(), motion.z * 0.3);
            player.setPos(player.getX(), target.getY() + 1.0, player.getZ());
         }
      } else if (this.towerMode.is("Jump") && player.onGround()) {
         player.jumpFromGround();
      }
   }

   private void holdEdge(Minecraft client, LocalPlayer player) {
      if (!player.onGround()) {
         this.releaseSneak();
      } else {
         Level level = client.level;
         double y = player.getY() - 0.5;
         double half = player.getBbWidth() / 2.0 + 0.25;
         boolean air = true;

         for (double dx : new double[]{-half, half}) {
            for (double dz : new double[]{-half, half}) {
               BlockPos pos = BlockPos.containing(player.getX() + dx, y, player.getZ() + dz);
               if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                  air = false;
               }
            }
         }

         if (air) {
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

   private static BlockHitResult findPlacement(Level level, BlockPos target) {
      Direction[] order = new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

      for (Direction direction : order) {
         BlockPos neighbour = target.relative(direction);
         BlockState state = level.getBlockState(neighbour);
         if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, neighbour).isEmpty() && state.getFluidState().isEmpty()) {
            Direction face = direction.getOpposite();
            Vec3 location = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            return new BlockHitResult(location, face, neighbour, false);
         }
      }

      return null;
   }

   private static int countBlocks(LocalPlayer player) {
      int total = 0;

      for (int i = 0; i < 9; i++) {
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
         for (int i = 0; i < 9; i++) {
            if (usable(player.getInventory().getItem(i))) {
               return i;
            }
         }

         return -1;
      }
   }

   private static boolean usable(ItemStack stack) {
      if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         return !(block instanceof EntityBlock) && !(block instanceof FallingBlock) && block.defaultBlockState().isSolidRender();
      } else {
         return false;
      }
   }
}
