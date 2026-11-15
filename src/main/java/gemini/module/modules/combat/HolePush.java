package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.HudOverlay;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.Holes;
import gemini.util.RotationManager;
import gemini.util.Targets;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pushes an enemy out of its hole with a piston at head height and something to power it.
 *
 * <p>A placed piston faces the player: it takes the opposite of the direction the player looks in most. So for a
 * piston that must face the target along {@code -d}, the player has to look along {@code d}, level. That look is
 * requested from {@link RotationManager} a tick before the piston goes down, so the server already has it when the
 * placement arrives, and the click itself runs inside {@link RotationManager#useWithAim} so the client predicts the
 * same facing.
 */
public final class HolePush extends Module {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private static final Predicate<ItemStack> PISTON = stack -> stack.is(Items.PISTON) || stack.is(Items.STICKY_PISTON);
   /** A plan that has not pushed after this many ticks is dropped and worked out again. */
   private static final int PLAN_TIMEOUT = 40;
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block you will place", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting targetRange = this.addSetting(new NumberSetting("Target Range", "Enemy search distance", 5.5, 2.0, 8.0, 0.5));
   private final ModeSetting power = this.addSetting(
      new ModeSetting("Power", "What powers the piston: a redstone block, or a redstone torch standing beside it", "Redstone Block", "Redstone Block", "Torch")
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Step Delay", "Ticks between piston and power", 1.0, 0.0, 6.0, 1.0));
   private final BooleanSetting silentAim = this.addSetting(
      new BooleanSetting("Silent Aim", "Turn in packets only; off turns the camera too (the piston needs the turn either way)", true)
   );
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render Plan", "Show the piston, power and destination blocks", true));
   private final BooleanSetting autoDisable = this.addSetting(new BooleanSetting("Auto Disable", "Switch off after one push", true));
   private Plan plan;
   private Step step = Step.IDLE;
   private int wait;
   private int age;

   public HolePush() {
      super("Hole Push", "Push an enemy out of its hole with a powered piston.", Category.COMBAT);
   }

   @Override
   public String info() {
      return this.power.is("Torch") ? "Torch" : "Block";
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   private void reset() {
      this.plan = null;
      this.step = Step.IDLE;
      this.wait = 0;
      this.age = 0;
   }

   private Predicate<ItemStack> powerItem() {
      return this.power.is("Torch") ? stack -> stack.is(Items.REDSTONE_TORCH) : stack -> stack.is(Items.REDSTONE_BLOCK);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.readyNoScreen(client)) {
         return;
      }

      LocalPlayer player = client.player;
      Level level = client.level;
      if (this.plan != null && (++this.age > PLAN_TIMEOUT || !this.plan.target.isAlive() || !this.plan.target.blockPosition().equals(this.plan.feet))) {
         // The target left the hole: that is the goal, whoever caused it.
         boolean pushed = this.plan.target.isAlive() && !this.plan.target.blockPosition().equals(this.plan.feet) && this.step != Step.PISTON;
         this.reset();
         if (pushed && this.autoDisable.get()) {
            HudOverlay.toast("Hole Push", "Target pushed out of its hole", HudOverlay.Severity.SUCCESS);
            this.setEnabled(false, false);
         }

         return;
      }

      if (this.plan == null) {
         if (BlockPlacer.pickSlot(player, PISTON) < 0 || BlockPlacer.pickSlot(player, this.powerItem()) < 0) {
            HudOverlay.toast("Hole Push", "Needs a piston and a " + (this.power.is("Torch") ? "redstone torch" : "redstone block") + " on the hotbar", HudOverlay.Severity.WARNING);
            this.setEnabled(false, false);
            return;
         }

         this.plan = this.makePlan(client, level, player);
         if (this.plan == null) {
            return;
         }

         this.step = Step.PISTON;
         this.age = 0;
         this.wait = 1;
      }

      float[] look = {this.plan.side.toYRot(), 0.0F};
      if (this.step == Step.PISTON) {
         boolean aimed = RotationManager.request(player, look, 0.0F, this.silentAim.get(), this.priority());
         if (this.wait > 0) {
            this.wait--;
            return;
         }

         if (!aimed) {
            return;
         }

         boolean[] placed = new boolean[1];
         RotationManager.useWithAim(player, () -> placed[0] = BlockPlacer.place(client, this.plan.piston, PISTON, true, true, false));
         BlockState state = level.getBlockState(this.plan.piston);
         if (placed[0] || state.getBlock() instanceof PistonBaseBlock) {
            if (state.getBlock() instanceof PistonBaseBlock && state.getValue(PistonBaseBlock.FACING) != this.plan.side.getOpposite()) {
               HudOverlay.toast("Hole Push", "The piston came out facing the wrong way", HudOverlay.Severity.ERROR);
               this.setEnabled(false, false);
               return;
            }

            this.step = Step.POWER;
            this.wait = this.delay.intValue();
         }
      } else if (this.step == Step.POWER) {
         if (this.wait > 0) {
            this.wait--;
            return;
         }

         if (!(level.getBlockState(this.plan.piston).getBlock() instanceof PistonBaseBlock)) {
            this.reset();
            return;
         }

         if (RotationManager.request(player, RotationManager.toward(player, this.plan.power), 0.0F, this.silentAim.get(), this.priority())
            && BlockPlacer.place(client, this.plan.power, this.powerItem(), true, true, false)) {
            this.step = Step.WAIT_PUSH;
         }
      }
   }

   /** A free spot: nothing to collide with, no fluid. */
   private static boolean clear(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
   }

   private Plan makePlan(Minecraft client, Level level, LocalPlayer player) {
      LivingEntity enemy = Targets.nearest(
         player,
         client.level.entitiesForRendering(),
         LivingEntity.class,
         this.targetRange.get(),
         living -> Targets.isCandidate(player, living, true, true, false, true) && Holes.isHole(level, living.blockPosition())
      );
      if (enemy == null) {
         return null;
      }

      BlockPos feet = enemy.blockPosition();
      BlockPos head = feet.above();
      Plan best = null;
      double bestDistance = Double.MAX_VALUE;
      for (Direction side : SIDES) {
         BlockPos piston = head.relative(side);
         // The far side at head height and the space above it must be open, or the push goes nowhere.
         BlockPos destination = head.relative(side.getOpposite());
         if (!BlockPlacer.replaceable(level, piston)
            || BlockPlacer.occupied(level, piston)
            || !BlockPlacer.inReach(player, piston, this.range.get())
            || BlockPlacer.findPlacement(level, piston, false) == null
            || !clear(level, destination)
            || !clear(level, destination.above())
            || !clear(level, head.above())) {
            continue;
         }

         BlockPos power = this.powerSpot(level, player, piston, side);
         if (power == null) {
            continue;
         }

         double distance = Vec3.atCenterOf(piston).distanceToSqr(player.getEyePosition());
         if (distance < bestDistance) {
            bestDistance = distance;
            best = new Plan(enemy, feet, side, piston, power, destination);
         }
      }

      return best;
   }

   /**
    * Where the power source goes: next to the piston but not in front of it (that is where the head extends).
    * A redstone block needs only a free spot with a neighbour to place against; a torch must stand on a solid block.
    */
   private BlockPos powerSpot(Level level, LocalPlayer player, BlockPos piston, Direction side) {
      Direction front = side.getOpposite();
      for (Direction dir : new Direction[]{side, Direction.UP, side.getClockWise(), side.getCounterClockWise(), Direction.DOWN}) {
         if (dir == front) {
            continue;
         }

         BlockPos pos = piston.relative(dir);
         if (!BlockPlacer.replaceable(level, pos) || BlockPlacer.occupied(level, pos) || !BlockPlacer.inReach(player, pos, this.range.get())) {
            continue;
         }

         if (this.power.is("Torch")) {
            BlockPos below = pos.below();
            // A torch under the piston would have to hang from it; only standing torches are planned.
            if (dir != Direction.DOWN && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
               return pos;
            }
         } else {
            // Once the piston is down it is a neighbour of every spot around it, so the block can be placed against it.
            return pos;
         }
      }

      return null;
   }

   @Override
   public void onRender3D(float partialTick) {
      Plan current = this.plan;
      if (!this.render.get() || current == null) {
         return;
      }

      Render3D.block(current.piston, 0xFF8FD3FF, true, true, 1.6F, 45, true);
      Render3D.block(current.power, 0xFFFF4A4A, true, true, 1.6F, 45, true);
      Render3D.block(current.destination, 0xFF6DFF8A, true, false, 1.4F, 0, true);
      Render3D.line(Vec3.atCenterOf(current.piston), Vec3.atCenterOf(current.destination), 0xFF6DFF8A, 1.6F, true);
   }

   private record Plan(LivingEntity target, BlockPos feet, Direction side, BlockPos piston, BlockPos power, BlockPos destination) {
   }

   private enum Step {
      IDLE,
      PISTON,
      POWER,
      WAIT_PUSH
   }
}
