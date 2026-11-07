package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** World checks added for LiquidBounce coverage. */
public final class LbWorldChecks {
   private LbWorldChecks() {
   }

   static boolean survival(ServerPlayer p) {
      return !p.isCreative() && !p.isSpectator();
   }

   public static final class BreakAim extends Check {
      private static final double GROW = 0.3;

      public BreakAim() {
         super("break-aim", "Break (Aim/Walls)", Check.Category.WORLD, "Starting to break a block the player isn't looking at, or one behind other blocks (Nuker, Fucker, PacketMine, AutoFarm, FreeCam).");
      }

      private static boolean aims(Vec3 eye, double yaw, double pitch, BlockPos pos) {
         return CombatChecks.rayHits(eye, direction(yaw, pitch), WorldChecks.fullBlock(pos), GROW, 7.0);
      }

      /** Current rotation or one of the last two the client sent. */
      private static boolean aimsRecently(ServerPlayer p, PlayerState s, BlockPos pos) {
         Vec3 eye = p.getEyePosition();
         if (aims(eye, p.getYRot(), p.getXRot(), pos)) {
            return true;
         }

         Iterator<double[]> it = s.rotations.iterator();

         for (int i = 0; it.hasNext() && i < 2; i++) {
            double[] r = it.next();
            if (aims(eye, r[1], r[2], pos)) {
               return true;
            }
         }

         return false;
      }

      /** Is any of 7 points of the block in clear sight (only the block itself in the way)? */
      private static boolean visible(ServerPlayer p, BlockPos pos) {
         ServerLevel w = p.level();
         Vec3 eye = p.getEyePosition();
         double x = pos.getX();
         double y = pos.getY();
         double z = pos.getZ();
         double[][] points = new double[][]{
            {0.5, 0.5, 0.5}, {0.5, 0.95, 0.5}, {0.5, 0.05, 0.5}, {0.05, 0.5, 0.5}, {0.95, 0.5, 0.5}, {0.5, 0.5, 0.05}, {0.5, 0.5, 0.95}
         };

         for (double[] pt : points) {
            Vec3 target = new Vec3(x + pt[0], y + pt[1], z + pt[2]);
            BlockHitResult hit = w.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) {
               return true;
            }
         }

         return false;
      }

      @Override
      public void onAction(ServerPlayer p, PlayerState s, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
         if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && survival(p)) {
            Data d = this.data(s, Data::new);
            if (aimsRecently(p, s, pos)) {
               d.pending = null;
               if (!visible(p, pos)) {
                  if (this.buffer(s, 1.0) >= 2.0) {
                     this.buffer(s, -1.0);
                     this.flag(p, "started breaking " + CheckEngine.blockId(p.level().getBlockState(pos)) + " through a wall");
                  }
               } else {
                  this.decay(s, 0.25);
               }
            } else {
               // The dig packet comes before this tick's rotation; judge again once that arrives.
               d.pending = pos.immutable();
               d.pendingTime = System.currentTimeMillis();
            }
         }
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (d.pending != null && m.rotated) {
            this.resolve(p, s, d, aims(p.getEyePosition(), m.yaw, m.pitch, d.pending));
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         if (d.pending != null && System.currentTimeMillis() - d.pendingTime > 300L) {
            this.resolve(p, s, d, aimsRecently(p, s, d.pending));
         }
      }

      private void resolve(ServerPlayer p, PlayerState s, Data d, boolean aimed) {
         BlockPos pos = d.pending;
         d.pending = null;
         if (aimed) {
            this.decay(s, 0.25);
         } else if (!InfernoMod.ENGINE.serverLagging() && this.buffer(s, 1.0) >= 3.0) {
            this.buffer(s, -1.0);
            AABB b = WorldChecks.fullBlock(pos);
            double angle = CombatChecks.angleTo(p.getEyePosition(), p.getYRot(), p.getXRot(), b.getCenter().x, b.getCenter().y, b.getCenter().z);
            this.flag(p, "started breaking a block " + Math.round(angle) + "° away from where they were looking");
         }
      }

      private static final class Data {
         BlockPos pending;
         long pendingTime;
      }
   }

   public static final class BreakProgress extends Check {
      public BreakProgress() {
         super("break-progress", "FastBreak (Progress)", Check.Category.WORLD, "Finishing blocks sooner than their break time with the tool, effects, water and air penalties (FastBreak, NoSlowBreak).");
      }

      @Override
      public void onAction(ServerPlayer p, PlayerState s, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
         if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && survival(p)) {
            Data d = this.data(s, Data::new);
            d.pos = pos.immutable();
            d.start = System.currentTimeMillis();
            d.startProgress = p.level().getBlockState(pos).getDestroyProgress(p, p.level(), pos);
         }
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         Data d = this.data(s, Data::new);
         if (d.pos == null || !d.pos.equals(pos) || !survival(p)) {
            return false;
         }

         long took = System.currentTimeMillis() - d.start;
         BlockState state = p.level().getBlockState(pos);
         // The most generous of start and end progress, in case the tool or effects changed mid-dig.
         float progress = Math.max(d.startProgress, state.getDestroyProgress(p, p.level(), pos));
         d.pos = null;
         if (progress <= 0.0F || progress >= 1.0F || Math.ceil(1.0 / progress) < 20.0 || InfernoMod.ENGINE.serverLagging()) {
            return false;
         }

         double ratio = Detect.breakRatio(took, progress);
         d.ratios.addLast(ratio);

         while (d.ratios.size() > 5) {
            d.ratios.removeFirst();
         }

         double min = InfernoConfig.get().limit("break-progress.min-ratio");
         int fast = 0;

         for (double r : d.ratios) {
            if (r < min) {
               fast++;
            }
         }

         if (fast >= 3) {
            d.ratios.clear();
            this.flag(p, "broke " + CheckEngine.blockId(state) + " in " + Math.round(ratio * 100.0) + "% of its break time (3 of the last 5 blocks too fast)");
            return InfernoConfig.get().cancelBlocks;
         }

         return false;
      }

      private static final class Data {
         BlockPos pos;
         long start;
         float startProgress;
         final Deque<Double> ratios = new ArrayDeque<>();
      }
   }

   public static final class LiquidPlace extends Check {
      public LiquidPlace() {
         super("liquid-place", "LiquidPlace", Check.Category.WORLD, "Placing blocks against water or lava, which the crosshair can't target (LiquidPlace, AirPlace modes, Scaffold).");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         ServerLevel w = p.level();
         BlockPos pos = hit.getBlockPos();
         BlockState state = w.getBlockState(pos);
         String item = String.valueOf(p.getMainHandItem().getItem()) + String.valueOf(p.getOffhandItem().getItem());
         if (!state.isAir() && !state.getFluidState().isEmpty() && state.getShape(w, pos).isEmpty() && !item.contains("lily_pad") && !item.contains("frogspawn")) {
            if (this.buffer(s, 1.0) >= 1.5) {
               this.buffer(s, -1.0);
               this.flag(p, "placed a block against " + CheckEngine.blockId(state));
               return true;
            }
         } else {
            this.decay(s, 0.1);
         }

         return false;
      }
   }

   public static final class TickInteract extends Check {
      public TickInteract() {
         super("tick-interact", "Place/Use (Per Tick)", Check.Category.WORLD, "Placing blocks or using items several times inside one client tick (Surround, AutoTrap, BlockIn, HoleFiller, FastExp, AutoBuff).");
      }

      @Override
      public void onClientTick(ServerPlayer p, PlayerState s, ClientTick tick) {
         if ((tick.places > 0 || tick.uses > 0) && System.currentTimeMillis() - tick.start < 150L && survival(p)) {
            // One click places at most once per hand; even two clicks inside 50 ms stay under these.
            if (tick.places >= 4 || tick.uses >= 6) {
               if (this.buffer(s, 1.0) >= 1.5) {
                  this.buffer(s, -1.0);
                  this.flag(p, tick.places >= 4 ? tick.places + " block placements in one tick" : tick.uses + " item uses in one tick");
               }
            } else {
               this.decay(s, 0.05);
            }
         }
      }
   }
}
