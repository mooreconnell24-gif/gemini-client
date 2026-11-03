package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class JesusModule extends Module {
   private static final double WATER_DRAG = 0.8;
   private static final double LAVA_DRAG = 0.5;
   private static final double WALK_STEP = 0.098;
   private static final double SPRINT_STEP = 0.1274;
   private static volatile JesusModule instance;
   private static volatile boolean active;
   private final ModeSetting waterMode = this.addSetting(
      new ModeSetting(
         "Water", "Solid walks on water; Bob floats; Dolphin leaps", "Bob", "Solid", "Bob", "Dolphin", "Off"
      )
   );
   private final ModeSetting lavaMode = this.addSetting(
      new ModeSetting(
         "Lava", "Solid walks on lava; Bob floats; Dolphin leaps", "Bob", "Solid", "Bob", "Dolphin", "Off"
      )
   );
   private final BooleanSetting flowing = this.addSetting(
      new BooleanSetting("Flowing Fluid", "Also stand on flowing water and lava", true)
   );
   private final NumberSetting offset = this.addSetting(
      new NumberSetting("Surface Offset", "Raises or lowers the surface you stand on, in blocks", 0.0, -0.3, 0.3, 0.01).showIf(this::anySolid)
   );
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Speed", "Walking speed multiplier while standing on a fluid", 1.0, 1.0, 2.0, 0.05).showIf(this::anySolid)
   );
   private final NumberSetting rise = this.addSetting(
      new NumberSetting("Rise Speed", "Upward speed when rising back to the fluid surface", 0.12, 0.03, 0.4, 0.01)
   );
   private final NumberSetting bobHeight = this.addSetting(
      new NumberSetting("Bob Height", "How far Bob mode swings up and down", 1.0, 0.0, 3.0, 0.1).showIf(() -> this.anyMode(JesusModule.Treatment.BOB))
   );
   private final NumberSetting leap = this.addSetting(
      new NumberSetting("Leap Power", "Upward speed of each Dolphin jump", 0.42, 0.2, 0.8, 0.02).showIf(() -> this.anyMode(JesusModule.Treatment.DOLPHIN))
   );
   private final BooleanSetting dipSneak = this.addSetting(
      new BooleanSetting("Dip On Sneak", "Hold sneak to sink down into the fluid", true)
   );
   private final BooleanSetting dipBurning = this.addSetting(
      new BooleanSetting("Dip If Burning", "Sink into water when you are on fire so it puts the fire out", true)
   );
   private final BooleanSetting dipFall = this.addSetting(
      new BooleanSetting("Dip On Fall", "Break through the surface after a long fall so the water catches you", true)
   );
   private final NumberSetting fallHeight = this.addSetting(
      new NumberSetting("Fall Height", "Fall distance, in blocks, that makes you break through the surface", 4.0, 1.0, 20.0, 1.0).showIf(this.dipFall::get)
   );
   private int surfaceTick = -1073741824;

   public JesusModule() {
      super("Jesus", "Walk on water and lava, or float and leap through it.", Category.MOVEMENT);
   }

   public static boolean active() {
      return active;
   }

   public static VoxelShape surface(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      JesusModule module = instance;
      if (module != null && context instanceof EntityCollisionContext entityContext) {
         LocalPlayer player = Minecraft.getInstance().player;
         return player != null && entityContext.getEntity() == player ? module.shapeFor(state, level, pos, context, player) : null;
      } else {
         return null;
      }
   }

   private boolean anySolid() {
      return this.anyMode(JesusModule.Treatment.SOLID);
   }

   private boolean anyMode(JesusModule.Treatment treatment) {
      return JesusModule.Treatment.of(this.waterMode) == treatment || JesusModule.Treatment.of(this.lavaMode) == treatment;
   }

   @Override
   public String info() {
      return this.waterMode.get().equals(this.lavaMode.get()) ? this.waterMode.get() : "W " + this.waterMode.get() + " / L " + this.lavaMode.get();
   }

   @Override
   public void onEnable() {
      instance = this;
      active = true;
   }

   @Override
   public void onDisable() {
      active = false;
   }

   private VoxelShape shapeFor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, LocalPlayer player) {
      if (!context.alwaysCollideWithFluid() && !context.isPlacement()) {
         FluidState fluid = state.getFluidState();
         boolean water = fluid.is(FluidTags.WATER);
         if (!water && !fluid.is(FluidTags.LAVA)) {
            return null;
         } else if (JesusModule.Treatment.of(water ? this.waterMode : this.lavaMode) != JesusModule.Treatment.SOLID) {
            return null;
         } else if (!this.flowing.get() && !fluid.isSource()) {
            return null;
         } else if (!player.isPassenger() && !player.isSpectator() && !player.getAbilities().flying && !this.dipping(player, water)) {
            if (level.getFluidState(pos.above()).getType().isSame(fluid.getType())) {
               return null;
            } else {
               double top = Math.min(1.0, fluid.getHeight(level, pos) + this.offset.get());
               if (top <= 0.0) {
                  return null;
               } else {
                  VoxelShape shape = Shapes.box(0.0, 0.0, 0.0, 1.0, top, 1.0);
                  if (!context.isAbove(shape, pos, true)) {
                     return null;
                  } else {
                     this.surfaceTick = player.tickCount;
                     return shape;
                  }
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean dipping(LocalPlayer player, boolean water) {
      if (this.dipSneak.get() && player.isShiftKeyDown()) {
         return true;
      } else {
         return water && this.dipBurning.get() && player.isOnFire() ? true : this.dipFall.get() && player.fallDistance > this.fallHeight.get();
      }
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && !player.isPassenger() && !player.isSpectator() && !player.getAbilities().flying) {
         boolean inWater = player.isInWater();
         if (!inWater && !player.isInLava()) {
            this.walkFaster(player);
         } else {
            JesusModule.Treatment treatment = JesusModule.Treatment.of(inWater ? this.waterMode : this.lavaMode);
            if (treatment != JesusModule.Treatment.OFF && !this.dipping(player, inWater)) {
               double drag = inWater ? 0.8 : 0.5;
               double lift = this.rise.get() / drag;
               boolean submerged = player.isEyeInFluid(inWater ? FluidTags.WATER : FluidTags.LAVA);
               Vec3 motion = player.getDeltaMovement();
               double vertical = motion.y;
               switch (treatment) {
                  case SOLID:
                     if (this.surfaceHeight(player) - player.getY() > 0.02) {
                        vertical = Math.max(vertical, lift);
                     }
                     break;
                  case BOB:
                     vertical = submerged ? Math.max(vertical, lift) : 0.02 + Math.sin(player.tickCount * 0.35) * 0.03 * this.bobHeight.get();
                     break;
                  case DOLPHIN:
                     vertical = submerged ? Math.max(vertical, lift) : this.leap.get();
               }

               if (vertical != motion.y) {
                  player.setDeltaMovement(motion.x, vertical, motion.z);
               }
            }
         }
      }
   }

   private double surfaceHeight(LocalPlayer player) {
      Level level = player.level();
      MutableBlockPos cursor = player.blockPosition().mutable();
      Fluid type = level.getFluidState(cursor).getType();

      for (int i = 0; i < 12 && level.getFluidState(cursor.above()).getType().isSame(type); i++) {
         cursor.move(0, 1, 0);
      }

      FluidState top = level.getFluidState(cursor);
      return cursor.getY() + top.getHeight(level, cursor) + this.offset.get();
   }

   private void walkFaster(LocalPlayer player) {
      double multiplier = this.speed.get();
      if (!(multiplier <= 1.0) && player.tickCount - this.surfaceTick <= 1 && player.input != null) {
         Input keys = player.input.keyPresses;
         if (keys.forward() || keys.backward() || keys.left() || keys.right()) {
            Vec3 motion = player.getDeltaMovement();
            double current = Math.hypot(motion.x, motion.z);
            if (!(current < 1.0E-4)) {
               boolean sprinting = player.isSprinting();
               double cap = multiplier * (sprinting ? 0.2806 : 0.2158) - (sprinting ? 0.1274 : 0.098);
               if (!(current >= cap)) {
                  double target = Math.min(cap, current * 1.3 + 0.01);
                  double scale = target / current;
                  player.setDeltaMovement(motion.x * scale, motion.y, motion.z * scale);
               }
            }
         }
      }
   }

   private static enum Treatment {
      SOLID,
      BOB,
      DOLPHIN,
      OFF;

      static JesusModule.Treatment of(ModeSetting setting) {
         String var1 = setting.get();

         return switch (var1) {
            case "Solid" -> SOLID;
            case "Bob" -> BOB;
            case "Dolphin" -> DOLPHIN;
            default -> OFF;
         };
      }
   }
}
