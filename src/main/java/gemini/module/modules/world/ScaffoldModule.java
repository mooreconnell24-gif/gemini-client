package gemini.module.modules.world;

import gemini.util.Inventories;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.ui.HudContext;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.PlacementFade;
import gemini.ui.font.UiFont;
import gemini.util.BlockPlacer;
import gemini.util.InfoText;
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
   private final NumberSetting expand = this.addSetting(new NumberSetting("Expand", "Extra blocks placed ahead of you", 0.0, 0.0, 4.0, 1.0));
   private final ModeSetting sprintMode = this.addSetting(
      new ModeSetting("Sprint", "On keeps you sprinting while you bridge; Off stops you sprinting; Vanilla leaves it to you", "On", "On", "Vanilla", "Off")
   );
   private final ModeSetting rotate = this.addSetting(
      new ModeSetting("Rotate", "How you face the block being placed: Silent turns without moving your camera, Visible turns the camera, None does not turn", "Silent", "Silent", "Visible", "None")
   );
   private final NumberSetting turnSpeed = this.addSetting(
      new NumberSetting("Turn Speed", "Most you turn per tick, in degrees", 95.0, 20.0, 180.0, 5.0).showIf(() -> !this.rotate.is("None"))
   );
   private final ModeSetting towerMode = this.addSetting(
      new ModeSetting(
         "Tower",
         "While holding Jump: Motion and Constant push you up, Jump hops in place, Sprint Jump keeps running and jumping and places a block under you at the top of each jump",
         "Motion",
         "Motion",
         "Constant",
         "Jump",
         "Sprint Jump",
         "Off"
      )
   );
   private final NumberSetting towerSpeed = this.addSetting(
      new NumberSetting("Tower Motion", "Upward speed while building up, in blocks per tick", 0.42, 0.25, 0.6, 0.02)
         .showIf(() -> this.towerMode.is("Motion") || this.towerMode.is("Constant"))
   );
   private final BooleanSetting keepY = this.addSetting(
      new BooleanSetting("Keep Y", "Keep placing at the height you started at and jump as you run, which bridges faster", false)
   );
   private final BooleanSetting clutchSearch = this.addSetting(new BooleanSetting("Clutch Search", "Look up to 3 blocks away for something to place against when there is nothing directly below, so it can catch you in mid-air", true));
   private final BooleanSetting autoRefill = this.addSetting(new BooleanSetting("Auto Refill", "Refill hotbar blocks from your inventory", true));
   private final BooleanSetting safeWalk = this.addSetting(new BooleanSetting("Safe Walk", "Sneak for a moment whenever you are over a gap, so you cannot fall off", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand on each placement", true));
   private final BooleanSetting keepSlot = this.addSetting(new BooleanSetting("Restore Slot", "Switch back to the original hotbar slot after placing", false));
   private final BooleanSetting sneakPause = this.addSetting(new BooleanSetting("Pause Sneak", "Pause placing while you sneak", false));
   private final BooleanSetting downwards = this.addSetting(new BooleanSetting("Downwards", "Place blocks one level lower while sneaking", false));
   private final BooleanSetting renderBlock = this.addSetting(new BooleanSetting("Highlight", "Show the blocks Scaffold places as fading boxes", true));
   private final BooleanSetting showCount = this.addSetting(
      new BooleanSetting("Show Count", "Show how many blocks are left in your hotbar under the crosshair, in red when they run low", true)
   );
   private final InfoText countText = new InfoText();
   private final PlacementFade fade = new PlacementFade(this, true, 286, this.renderBlock::get);
   private final InfoText infoText = new InfoText();
   private static final Direction[] FACE_ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
   private int cooldown;
   private int startY;
   private int towerTicks;
   private boolean sneaking;
   private int blockCount;

   public ScaffoldModule() {
      super("Scaffold", "Places blocks under you as you walk so you can bridge over gaps.", Category.WORLD);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Tower");
      names.add("BlockCounter");
      names.add("Block Counter");
      return names;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      this.startY = player == null ? 0 : Mth.floor(player.getY() - 0.01);
      this.sneaking = false;
      this.towerTicks = 0;
      this.cooldown = 0;
      this.fade.clear();
   }

   @Override
   public void onDisable() {
      this.releaseSneak();
      this.fade.clear();
   }

   @Override
   public void onWorldChange() {
      this.fade.clear();
      LocalPlayer player = Minecraft.getInstance().player;
      this.startY = player == null ? 0 : Mth.floor(player.getY() - 0.01);
   }

   /** The block count is taken once per tick in {@link #onTick}; the text is only rebuilt when it changes. */
   @Override
   public String info() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      boolean towering = player != null && this.towering(client, player);
      return this.infoText.whole(this.blockCount, towering ? " · Tower" : "");
   }

   private boolean towering(Minecraft client, LocalPlayer player) {
      if (this.towerMode.is("Off") || !client.options.keyJump.isDown()) {
         return false;
      }

      // Sprint Jump is the one tower mode meant for running; the others only tower in place unless bridging upward.
      return this.towerMode.is("Sprint Jump") || !MoveUtil.hasInput(player) || !this.keepY.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gameMode != null && client.gui.screen() == null && !player.isSpectator()) {
         this.blockCount = countBlocks(player);
         if (this.autoRefill.get() && findBlockSlot(player) == -1) {
            refillHotbarBlocks(client, player);
         }

         int slot = findBlockSlot(player);
         if (slot == -1) {
            this.releaseSneak();
            return;
         }

         if (player.onGround() && !this.keepY.get()) {
            this.startY = Mth.floor(player.getY() - 0.01);
         } else if (player.getY() < this.startY) {
            this.startY = Mth.floor(player.getY() - 0.01);
         }

         boolean towering = this.towering(client, player);
         boolean sprintTower = towering && this.towerMode.is("Sprint Jump");
         if (!towering) {
            this.towerTicks = 0;
         }

         this.applySprintControl(client, player, towering && !sprintTower);
         if (sprintTower) {
            this.sprintJump(player);
         }
         if (this.safeWalk.get() && !towering) {
            this.holdEdge(client, player);
         } else {
            this.releaseSneak();
         }

         if (this.keepY.get() && MoveUtil.hasInput(player) && player.onGround() && !client.options.keyJump.isDown()) {
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
         if (sprintTower) {
            // The block under the feet is placed once the feet have risen clear of it, so it never overlaps the player.
            targetY = Mth.floor(player.getY()) - 1;
         } else if (towering) {
            targetY = Mth.floor(player.getY() - 0.05);
         } else if (this.downwards.get() && MoveUtil.physicalKeyDown(client.options.keyShift)) {
            targetY = Mth.floor(player.getY() - 0.01) - 1;
            this.startY = targetY;
         } else if (this.keepY.get()) {
            targetY = this.startY;
         } else if (player.onGround()) {
            targetY = Mth.floor(player.getY() - 0.01);
         } else {
            targetY = this.startY;
         }

         BlockPos feet = new BlockPos(Mth.floor(player.getX()), targetY, Mth.floor(player.getZ()));
         List<BlockPos> targets = new ArrayList<>();
         targets.add(feet);
         Vec3 moveDir = MoveUtil.direction(player);
         if (moveDir != null && (!towering || sprintTower)) {
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

         ScaffoldModule.PlacementPlan plan = null;

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
            RotationManager.request(player, idleRot[0], idleRot[1], this.turnSpeed.floatValue(), !this.rotate.is("Visible"), ActionPriority.WORLD_UTILITY, 10.0F);
         }

         if (plan != null) {
            BlockHitResult hit = plan.hit;
            if (!this.rotate.is("None")) {
               boolean quiet = !this.rotate.is("Visible");
               float[] placeRot = this.computeBridgeRotation(player, plan.hit);
               // The turn goes through RotationManager's spring (Rotations settings) at Turn Speed instead of jumping there.
               RotationManager.request(player, placeRot[0], placeRot[1], this.turnSpeed.floatValue(), quiet, ActionPriority.WORLD_UTILITY, 16.0F);
            }

            if (!Hands.claimMainHand()) {

               return; // a survival module is using the hand this tick

            }


            int previous = player.getInventory().getSelectedSlot();
            if (previous != slot) {
               player.getInventory().setSelectedSlot(slot);
            }

            boolean placed = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction();
            if (this.swing.get()) {
               Swing.interact(player, InteractionHand.MAIN_HAND);
            }

            if (this.keepSlot.get() && previous != slot) {
               player.getInventory().setSelectedSlot(previous);
            }

            if (placed) {
               this.fade.record(plan.placePos);
            }

            if (towering && !sprintTower) {
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
         if (this.sprintMode.is("Off")) {
            player.setSprinting(false);
         } else if (this.sprintMode.is("On") && !player.isCrouching()) {
            player.setSprinting(true);
         }
      }
   }

   private float[] computeBridgeRotation(LocalPlayer player, BlockHitResult hit) {
      float backwards = Mth.wrapDegrees(movementYaw(player) + 180.0F);
      if (hit == null) {
         return new float[]{backwards, 79.5F};
      }

      // Mostly the exact angle to the clicked face, eased towards "looking back along the bridge" so the head
      // does not whip round between placements.
      float[] exact = RotationManager.toward(player, hit.getLocation());
      float moveYaw = MoveUtil.hasInput(player) ? backwards : exact[0];
      float blendedYaw = moveYaw + Mth.wrapDegrees(exact[0] - moveYaw) * 0.65F;
      return new float[]{Mth.wrapDegrees(blendedYaw), Mth.clamp(exact[1], 70.0F, 88.5F)};
   }

   private static float movementYaw(LocalPlayer player) {
      Vec3 dir = MoveUtil.direction(player);
      return dir != null ? (float)(Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F : player.getYRot();
   }

   /**
    * Sprint-jump towering: keep sprinting while a direction is held and jump the moment the feet touch down. The
    * block under the feet is then placed by the normal tick once the jump has carried the feet above it, and the
    * landing on that block starts the next jump. Plain vanilla jumps; no motion is added.
    */
   private void sprintJump(LocalPlayer player) {
      if (MoveUtil.hasInput(player) && player.input != null && player.input.hasForwardImpulse() && !player.isCrouching()
         && player.getFoodData().getFoodLevel() > 6) {
         player.setSprinting(true);
      }

      if (player.onGround() && !player.isInWater() && !player.isInLava()) {
         player.jumpFromGround();
         this.towerTicks++;
      }
   }

   private void applyTowerMotion(LocalPlayer player, BlockPos target) {
      this.towerTicks++;
      Vec3 motion = player.getDeltaMovement();
      double horizScale = MoveUtil.hasInput(player) ? 0.65 : 0.15;
      if (this.towerMode.is("Motion")) {
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
         BlockPos belowCenter = new BlockPos(Mth.floor(player.getX()), Mth.floor(player.getY() - 0.05), Mth.floor(player.getZ()));
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
   public void onRenderHud(HudContext hud) {
      if (this.showCount.get() && hud.client.gui.screen() == null) {
         int count = this.blockCount;
         String text = this.countText.whole(count, count == 1 ? " block" : " blocks");
         UiFont.draw(hud.graphics, text, hud.width / 2 - UiFont.width(text) / 2, hud.height / 2 + 14, count > 16 ? -1 : -43691, true);
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.renderBlock.get()) {
         this.fade.render();
      }
   }

   private static boolean replaceable(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.canBeReplaced();
   }

   private static boolean solidSupport(Level level, BlockPos pos) {
      return BlockPlacer.solidSupport(level, pos);
   }

   private static ScaffoldModule.PlacementPlan findPlacementWithClutch(Level level, LocalPlayer player, BlockPos goal, int maxRadius) {
      BlockHitResult direct = findDirectHit(level, player, goal);
      if (direct != null) {
         return new ScaffoldModule.PlacementPlan(goal, direct);
      } else if (maxRadius <= 1) {
         return null;
      } else {
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

         candidates.sort(Comparator.comparingDouble(posx -> posx.distSqr(goal)));

         for (BlockPos bridgeStep : candidates) {
            BlockHitResult stepHit = findDirectHit(level, player, bridgeStep);
            if (stepHit != null) {
               return new ScaffoldModule.PlacementPlan(bridgeStep, stepHit);
            }
         }

         return null;
      }
   }

   private static BlockHitResult findDirectHit(Level level, LocalPlayer player, BlockPos target) {
      Vec3 eye = player.getEyePosition();
      BlockHitResult best = null;
      double bestDist = Double.MAX_VALUE;

      for (Direction direction : FACE_ORDER) {
         BlockPos neighbour = target.relative(direction);
         if (solidSupport(level, neighbour)) {
            Direction face = direction.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            double dist = eye.distanceToSqr(hitVec);
            // Only faces the eye is in front of: a face pointing away can never be hit by a look ray.
            double facing = (eye.x - hitVec.x) * face.getStepX() + (eye.y - hitVec.y) * face.getStepY() + (eye.z - hitVec.z) * face.getStepZ();
            if (facing > 1.0E-3 && dist <= 22.5 && dist < bestDist) {
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
            Inventories.moveToHotbar(client, player, i, player.getInventory().getSelectedSlot());
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
