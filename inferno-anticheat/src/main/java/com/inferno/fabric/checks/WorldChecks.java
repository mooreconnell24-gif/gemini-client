package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldChecks {
   private WorldChecks() {
   }

   static AABB fullBlock(BlockPos pos) {
      return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
   }

   public static final class BlockReach extends Check {
      public BlockReach() {
         super("block-reach", "Reach (Blocks)", Check.Category.WORLD, "Placing or breaking blocks from too far away.");
      }

      private boolean tooFar(ServerPlayer p, PlayerState s, BlockPos pos) {
         double d = CombatChecks.distanceToBox(p.getEyePosition(), WorldChecks.fullBlock(pos));
         if (d > InfernoConfig.get().limit("block-reach.max-distance")) {
            if (this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, round(d) + " blocks away");
               return true;
            }
         } else {
            this.decay(s, 0.25);
         }

         return false;
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         return this.tooFar(p, s, hit.getBlockPos());
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         return this.tooFar(p, s, pos);
      }
   }

   public static final class FastPlace extends Check {
      public FastPlace() {
         super("fastplace", "FastPlace", Check.Category.WORLD, "Placing blocks faster than humanly possible.");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         Deque<Long> places = this.data(s, ArrayDeque::new);
         long now = System.currentTimeMillis();
         places.addLast(now);

         while (!places.isEmpty() && now - places.peekFirst() > 1000L) {
            places.removeFirst();
         }

         double max = InfernoConfig.get().limit("fastplace.max-per-second");
         if (places.size() > max) {
            places.clear();
            this.flag(p, "more than " + Math.round(max) + " blocks in a second");
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class Nuker extends Check {
      public Nuker() {
         super("nuker", "Nuker", Check.Category.WORLD, "Breaking many blocks at once or blocks behind the player.");
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         Deque<Long> breaks = this.data(s, ArrayDeque::new);
         long now = System.currentTimeMillis();
         breaks.addLast(now);

         while (!breaks.isEmpty() && now - breaks.peekFirst() > 1000L) {
            breaks.removeFirst();
         }

         Vec3 eye = p.getEyePosition();
         double cx = pos.getX() + 0.5;
         double cy = pos.getY() + 0.5;
         double cz = pos.getZ() + 0.5;
         double angle = CombatChecks.angleTo(eye, p.getYRot(), p.getXRot(), cx, cy, cz);
         double distSq = (cx - eye.x) * (cx - eye.x) + (cy - eye.y) * (cy - eye.y) + (cz - eye.z) * (cz - eye.z);
         double maxBreaks = InfernoConfig.get().limit("nuker.max-per-second");
         boolean tooMany = breaks.size() > maxBreaks;
         boolean behind = angle > 100.0 && distSq > 2.25;
         if (!tooMany && !behind) {
            this.decay(s, 0.25);
         } else {
            double d = this.buffer(s, 1.0);
            int n = tooMany ? 1 : 3;
            if (d >= n) {
               breaks.clear();
               this.flag(p, tooMany ? "more than " + Math.round(maxBreaks) + " blocks in a second" : "broke a block " + Math.round(angle) + "° away from view");
               return true;
            }
         }

         return false;
      }
   }

   public static final class ScaffoldFace extends Check {
      public ScaffoldFace() {
         super("scaffold-face", "Scaffold (Face)", Check.Category.WORLD, "Placing blocks on a side of a block the player can't see (Scaffold).");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         ServerLevel w = p.level();
         BlockPos pos;
         BlockState state = w.getBlockState(pos = hit.getBlockPos());
         if (!state.isAir() && !state.canBeReplaced()) {
            VoxelShape shape = state.getCollisionShape(w, pos);
            AABB box = shape.isEmpty() ? WorldChecks.fullBlock(pos) : shape.bounds().move(pos);
            Direction side = hit.getDirection();
            int nx = side.getStepX();
            int ny = side.getStepY();
            int nz = side.getStepZ();
            Vec3 eye = p.getEyePosition();
            double tolerance = 0.1 + s.lastHorizontal;
            double eyeCoord = nx != 0 ? eye.x : (ny != 0 ? eye.y : eye.z);
            int normal = nx + ny + nz;
            double plane;
            if (nx != 0) {
               plane = normal > 0 ? Math.min(box.maxX, pos.getX() + 1) : Math.max(box.minX, pos.getX());
            } else if (ny != 0) {
               plane = normal > 0 ? Math.min(box.maxY, pos.getY() + 1) : Math.max(box.minY, pos.getY());
            } else {
               plane = normal > 0 ? Math.min(box.maxZ, pos.getZ() + 1) : Math.max(box.minZ, pos.getZ());
            }

            boolean behind = normal > 0 ? eyeCoord < plane - tolerance : eyeCoord > plane + tolerance;
            double[] dir = direction(p.getYRot(), p.getXRot());
            boolean lookingAway = dir[0] * nx + dir[1] * ny + dir[2] * nz > 0.3;
            if (!behind && !lookingAway) {
               this.decay(s, 0.2);
            } else if (this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, behind ? "placed on a hidden side of a block" : "placed on a side while looking away");
               return true;
            }

            return false;
         } else {
            return false;
         }
      }
   }
}
