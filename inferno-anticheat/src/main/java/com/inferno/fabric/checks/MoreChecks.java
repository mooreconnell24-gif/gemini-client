package com.inferno.fabric.checks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MoreChecks {
   private MoreChecks() {
   }

   static boolean slowsDown(ItemStack stack) {
      UseEffects fx = (UseEffects)stack.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT);
      return !fx.canSprint() && fx.speedMultiplier() <= 0.2001F;
   }

   public static final class AimPattern extends Check {
      public AimPattern() {
         super("aim-pattern", "Aim (Robotic)", Check.Category.COMBAT, "Perfectly repeated rotation steps during a fight (smooth aimbot).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.rotated && m.time - s.lastAttack <= 2000L) {
            Deque<float[]> history = this.data(s, ArrayDeque::new);
            history.addFirst(new float[]{m.yawDelta, m.pitchDelta});

            while (history.size() > 20) {
               history.removeLast();
            }

            if (history.size() >= 20) {
               int repeats = 0;
               float[] prev = null;

               for (float[] r : history) {
                  if (prev != null && Math.abs(r[0]) > 1.0F && r[0] == prev[0] && r[1] == prev[1]) {
                     repeats++;
                  }

                  prev = r;
               }

               if (repeats >= 6) {
                  history.clear();
                  this.flag(p, repeats + " identical rotation steps in 20 ticks");
               }
            }
         }
      }
   }

   public static final class AimSilent extends Check {
      public AimSilent() {
         super("aim-silent", "Aim (Silent)", Check.Category.COMBAT, "Rotation flicks to the target for one hit, then snaps back (silent aim).");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (s.rotations.size() < 2) {
            return false;
         }

         Iterator<double[]> it = s.rotations.iterator();
         double[] now = it.next();
         double[] before = it.next();
         if (System.currentTimeMillis() - now[0] > 100.0) {
            return false;
         }

         double flick = Math.abs(Move.wrap((float)(now[1] - before[1]))) + Math.abs(now[2] - before[2]);
         if (flick >= 30.0) {
            MoreChecks.AimSilent.Pending pending = this.data(s, MoreChecks.AimSilent.Pending::new);
            pending.time = System.currentTimeMillis();
            pending.baseYaw = before[1];
            pending.basePitch = before[2];
            pending.flick = flick;
            pending.updates = 0;
         }

         return false;
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MoreChecks.AimSilent.Pending pending = this.data(s, MoreChecks.AimSilent.Pending::new);
         if (m.rotated && pending.time != 0L) {
            if (m.time - pending.time <= 200L && ++pending.updates <= 2) {
               double back = Math.abs(Move.wrap((float)(m.yaw - pending.baseYaw))) + Math.abs(m.pitch - pending.basePitch);
               if (back <= 6.0) {
                  pending.time = 0L;
                  if (this.buffer(s, 1.0) >= 2.0) {
                     this.flag(p, "flicked " + Math.round(pending.flick) + "° to hit, then snapped back");
                  }
               }
            } else {
               pending.time = 0L;
               this.decay(s, 0.2);
            }
         }
      }

      private static final class Pending {
         long time;
         double baseYaw;
         double basePitch;
         double flick;
         int updates;
      }
   }

   public static final class FastClimb extends Check {
      public FastClimb() {
         super("fastclimb", "FastClimb", Check.Category.MOVEMENT, "Climbing ladders and vines too fast.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MoreChecks.FastClimb.Climb c = this.data(s, MoreChecks.FastClimb.Climb::new);
         if (m.checkable() && m.climbing && !m.liquid && !(m.dy <= 0.2)) {
            if (++c.ticks >= 4) {
               c.ticks = 0;
               this.flag(p, "climbing at " + round(m.dy) + " b/t");
               m.setback();
            }
         } else {
            if (m.moved) {
               c.ticks = 0;
            }
         }
      }

      private static final class Climb {
         int ticks;
      }
   }

   public static final class NoSlow extends Check {
      public NoSlow() {
         super("noslow", "NoSlow", Check.Category.MOVEMENT, "Not slowing down while using items.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MoreChecks.NoSlow.Use use = this.data(s, MoreChecks.NoSlow.Use::new);
         if (p.isUsingItem() && MoreChecks.slowsDown(p.getUseItem())) {
            if (use.since == 0L) {
               use.since = m.time;
            }

            if (m.checkable() && m.time - use.since >= 400L && m.ground && m.lastGround && !(Math.abs(m.dy) > 0.001) && !m.ice && !m.liquid) {
               if (m.horizontal > 0.17) {
                  if (this.buffer(s, 1.0) >= 5.0) {
                     this.buffer(s, -3.0);
                     this.flag(p, round(m.horizontal) + " b/t while using an item");
                     m.setback();
                  }
               } else {
                  this.decay(s, 1.0);
               }
            }
         } else {
            use.since = 0L;
         }
      }

      private static final class Use {
         long since;
      }
   }

   public static final class OmniSprint extends Check {
      public OmniSprint() {
         super("omnisprint", "Sprint (Omni)", Check.Category.MOVEMENT, "Sprinting backwards or sideways.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && p.isSprinting() && !(m.horizontal < 0.2) && m.ground && m.lastGround && !m.ice && !m.liquid && !(Math.abs(m.yawDelta) > 20.0F)) {
            double yaw = Math.toRadians(m.yaw);
            double dot = (m.dx * -Math.sin(yaw) + m.dz * Math.cos(yaw)) / m.horizontal;
            if (dot < -0.2) {
               if (this.buffer(s, 1.0) >= 6.0) {
                  this.buffer(s, -4.0);
                  this.flag(p, "sprinting " + Math.round(Math.toDegrees(Math.acos(Math.max(-1.0, dot)))) + "° from facing");
               }
            } else {
               this.decay(s, 0.5);
            }
         } else {
            this.decay(s, 0.2);
         }
      }
   }

   public static final class Phase extends Check {
      public Phase() {
         super("phase", "Phase", Check.Category.MOVEMENT, "Walking or clipping through solid blocks.");
      }

      private static boolean solidAt(ServerLevel w, double x, double y, double z) {
         BlockPos pos = BlockPos.containing(x, y, z);
         BlockState state = w.getBlockState(pos);
         if (!state.isSolidRender()) {
            return false;
         }

         VoxelShape shape = state.getCollisionShape(w, pos);
         if (shape.isEmpty()) {
            return false;
         }

         AABB box = shape.bounds().move(pos);
         return box.minY <= y && box.maxY > y + 0.01;
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         ServerLevel w = p.level();
         if (m.checkable() && !solidAt(w, m.fromX, m.fromY + 0.5, m.fromZ)) {
            double distance = Math.sqrt(m.horizontal * m.horizontal + m.dy * m.dy);
            int steps = Math.max(1, (int)Math.ceil(distance / 0.25));

            for (int i = 1; i <= steps; i++) {
               double t = (double)i / steps;
               double x = m.fromX + m.dx * t;
               double y = m.fromY + m.dy * t;
               double z = m.fromZ + m.dz * t;
               if (solidAt(w, x, y + 0.5, z) || solidAt(w, x, y + 0.2, z)) {
                  this.flag(p, i == steps ? "moved inside a solid block" : "moved through a solid block");
                  m.setback();
                  return;
               }
            }
         }
      }
   }

   public static final class Tower extends Check {
      public Tower() {
         super("tower", "Scaffold (Tower)", Check.Category.WORLD, "Pillaring up faster than jumping allows.");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         Direction side = hit.getDirection();
         BlockPos clicked = hit.getBlockPos();
         int px = clicked.getX() + side.getStepX();
         int py = clicked.getY() + side.getStepY();
         int pz = clicked.getZ() + side.getStepZ();
         boolean underFeet = px == (int)Math.floor(p.getX()) && pz == (int)Math.floor(p.getZ()) && py < p.getY() && py >= p.getY() - 2.0;
         Deque tower = this.data(s, ArrayDeque::new);
         long now = System.currentTimeMillis();
         if (underFeet && !CheckEngine.special(p)) {
            if (!tower.isEmpty() && (((long[])tower.peekLast())[1] != py - 1 || now - ((long[])tower.peekLast())[0] > 1500L)) {
               tower.clear();
            }

            tower.addLast(new long[]{now, py});

            while (tower.size() > 8) {
               tower.removeFirst();
            }

            if (tower.size() < 6) {
               return false;
            } else {
               double rate = (tower.size() - 1) / Math.max(0.05, (now - ((long[])tower.peekFirst())[0]) / 1000.0);
               if (rate > 2.6) {
                  tower.clear();
                  this.flag(p, round(rate) + " blocks/s upwards");
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            tower.clear();
            return false;
         }
      }
   }
}
