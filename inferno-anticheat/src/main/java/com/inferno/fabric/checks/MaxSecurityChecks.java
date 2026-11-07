package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class MaxSecurityChecks {
   private MaxSecurityChecks() {
   }

   public static final class AutoBlock extends Check {
      public AutoBlock() {
         super("autoblock", "AutoBlock", Check.Category.COMBAT, "Blocking/using item in the same tick as attacking.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (p.isUsingItem()) {
            if (this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, "attack while using item");
               return true;
            }
         } else {
            this.decay(s, 0.3);
         }

         return false;
      }
   }

   public static final class Clip extends Check {
      public Clip() {
         super("clip", "Clip", Check.Category.MOVEMENT, "Teleporting through solid blocks (PacketFly / Phase).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !(m.horizontal + Math.abs(m.dy) < 0.6)) {
            ServerLevel w = p.level();
            Vec3 from = new Vec3(m.fromX, m.fromY + 0.9, m.fromZ);
            Vec3 to = new Vec3(m.toX, m.toY + 0.9, m.toZ);
            BlockHitResult hit = w.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, p));
            if (hit.getType() == Type.BLOCK) {
               double traveled = from.distanceTo(to);
               double toHit = from.distanceTo(hit.getLocation());
               if (traveled > 0.8 && toHit < traveled - 0.35) {
                  if (this.buffer(s, 1.0) >= 1.5) {
                     this.flag(p, "moved through blocks (" + round(traveled) + "b)");
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.3);
               }
            }
         }
      }
   }

   public static final class CriticalsB extends Check {
      public CriticalsB() {
         super("criticals-b", "Criticals B", Check.Category.COMBAT, "Critical hits without a real fall.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (!(target instanceof LivingEntity)) {
            return false;
         }

         if (!p.onGround() && !(p.fallDistance > 0.1F)) {
            if (!p.onGround() && p.fallDistance < 0.05F && s.airTicks <= 1 && Math.abs(s.lastDy) < 0.08 && this.buffer(s, 1.0) >= 3.0) {
               this.flag(p, "packet critical");
               return InfernoConfig.get().cancelHits;
            } else {
               return false;
            }
         } else {
            this.decay(s, 0.2);
            return false;
         }
      }
   }

   public static final class KillAuraAngle extends Check {
      public KillAuraAngle() {
         super("killaura-angle", "KillAura (Angle)", Check.Category.COMBAT, "Attacking outside realistic FOV.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         Vec3 eye = p.getEyePosition();
         AABB box = target.getBoundingBox();
         double ang = CombatChecks.angleTo(
            eye, p.getYRot(), p.getXRot(), (box.minX + box.maxX) / 2.0, box.minY + (box.maxY - box.minY) * 0.55, (box.minZ + box.maxZ) / 2.0
         );
         double max = InfernoConfig.get().limit("killaura-angle.max-deg");
         if (ang > max) {
            if (this.buffer(s, 1.0) >= 1.5) {
               this.flag(p, Math.round(ang) + "° off target");
               return true;
            }
         } else {
            this.decay(s, 0.4);
         }

         return false;
      }
   }

   public static final class KillAuraKeepSprint extends Check {
      public KillAuraKeepSprint() {
         super("killaura-sprint", "KillAura (Sprint)", Check.Category.COMBAT, "Keeping sprint through attacks unnaturally.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (!p.isSprinting()) {
            this.decay(s, 0.2);
            return false;
         }

         MaxSecurityChecks.KillAuraKeepSprint.Data d = this.data(s, MaxSecurityChecks.KillAuraKeepSprint.Data::new);
         long now = System.currentTimeMillis();
         if (now - d.last < 400L) {
            d.streak++;
            if (d.streak >= 8) {
               d.streak = 0;
               this.flag(p, "sprint-attack streak");
               return InfernoConfig.get().cancelHits;
            }
         } else {
            d.streak = 1;
         }

         d.last = now;
         return false;
      }

      private static final class Data {
         long last;
         int streak;
      }
   }

   public static final class MotionA extends Check {
      public MotionA() {
         super("motion-a", "Motion A", Check.Category.MOVEMENT, "Impossible vertical motion (fly / longjump / bunny).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable()) {
            if (m.dy > 0.6 && m.lastGround) {
               this.flag(p, "jump dy=" + round(m.dy));
               m.setback();
            } else {
               if (m.airborne() && m.dy > 0.42 && s.airTicks > 1) {
                  if (this.buffer(s, 1.0) >= 1.5) {
                     this.flag(p, "air boost dy=" + round(m.dy));
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.2);
               }
            }
         }
      }
   }

   public static final class PacketFly extends Check {
      public PacketFly() {
         super("packet-fly", "Packet Fly", Check.Category.MOVEMENT, "Alternating ground spoof / packet-fly patterns.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable()) {
            MaxSecurityChecks.PacketFly.Data d = this.data(s, MaxSecurityChecks.PacketFly.Data::new);
            boolean claim = m.clientGround;
            boolean real = m.ground;
            if (claim != real) {
               d.mismatch++;
            } else {
               d.mismatch = Math.max(0, d.mismatch - 1);
            }

            if (!real && d.lastClaim != claim) {
               d.flips++;
               if (d.flips >= 6 && d.mismatch >= 4) {
                  d.flips = 0;
                  this.flag(p, "packet-fly ground flip x" + d.mismatch);
                  m.setback();
               }
            } else if (real) {
               d.flips = Math.max(0, d.flips - 1);
            }

            d.lastClaim = claim;
         }
      }

      private static final class Data {
         boolean lastClaim;
         int flips;
         int mismatch;
      }
   }

   public static final class PingSpoof extends Check {
      public PingSpoof() {
         super("ping-spoof", "Ping Spoof", Check.Category.WORLD, "Latency claims that don't match packet cadence.");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         int ping = p.connection.latency();
         MaxSecurityChecks.PingSpoof.Data d = this.data(s, MaxSecurityChecks.PingSpoof.Data::new);
         long gap = System.currentTimeMillis() - s.lastPositionChange;
         if (ping > 600 && gap < 80L && !s.exempt()) {
            if (++d.bad >= 40) {
               d.bad = 0;
               this.flag(p, "ping " + ping + "ms but moves every " + gap + "ms");
            }
         } else {
            d.bad = Math.max(0, d.bad - 1);
         }
      }

      private static final class Data {
         int bad;
      }
   }

   public static final class ScaffoldRot extends Check {
      public ScaffoldRot() {
         super("scaffold-rot", "Scaffold (Rot)", Check.Category.WORLD, "Impossible pitch while placing blocks under feet.");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         BlockPos place = hit.getBlockPos().relative(hit.getDirection());
         double py = p.getY();
         if (!(place.getY() + 1.0 < py - 0.2) && !(place.getY() > py + 0.5)) {
            float pitch = p.getXRot();
            if (pitch < 40.0F && s.airTicks <= 1) {
               double dx = place.getX() + 0.5 - p.getX();
               double dz = place.getZ() + 0.5 - p.getZ();
               if (dx * dx + dz * dz < 2.5 && this.buffer(s, 1.0) >= 3.0) {
                  this.flag(p, "scaffold pitch " + round(pitch));
                  return InfernoConfig.get().cancelBlocks;
               }
            } else {
               this.decay(s, 0.2);
            }

            return false;
         } else {
            return false;
         }
      }
   }

   public static final class Simulation extends Check {
      public Simulation() {
         super("simulation", "Simulation", Check.Category.MOVEMENT, "Position disagrees with predicted physics.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (!m.airborne()) {
            this.data(s, MaxSecurityChecks.Simulation.Data::new).valid = false;
         } else {
            MaxSecurityChecks.Simulation.Data d = this.data(s, MaxSecurityChecks.Simulation.Data::new);
            if (d.valid && m.time - d.time <= 80L) {
               double predX = d.vx * 0.91;
               double predZ = d.vz * 0.91;
               double predY = (d.vy - 0.08) * 0.98;
               double errH = Math.hypot(m.dx - predX, m.dz - predZ);
               double errV = Math.abs(m.dy - predY);
               double tolH = InfernoConfig.get().strictMode ? 0.08 : 0.14;
               double tolV = InfernoConfig.get().strictMode ? 0.06 : 0.12;
               if (!(errH > tolH) && !(errV > tolV)) {
                  this.decay(s, 0.3);
               } else if (this.buffer(s, 1.0) >= (InfernoConfig.get().strictMode ? 2.0 : 4.0)) {
                  this.buffer(s, -1.5);
                  this.flag(p, "sim err h=" + round(errH) + " v=" + round(errV));
                  m.setback();
               }

               d.vx = m.dx;
               d.vz = m.dz;
               d.vy = m.dy;
               d.time = m.time;
            } else {
               d.vx = m.dx;
               d.vz = m.dz;
               d.vy = m.dy;
               d.time = m.time;
               d.valid = true;
            }
         }
      }

      private static final class Data {
         double vx;
         double vy;
         double vz;
         long time;
         boolean valid;
      }
   }

   public static final class TimerB extends Check {
      public TimerB() {
         super("timer-b", "Timer B", Check.Category.MOVEMENT, "Burst of move packets faster than 20 TPS.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.moved) {
            MaxSecurityChecks.TimerB.Data d = this.data(s, MaxSecurityChecks.TimerB.Data::new);
            long now = m.time;
            d.times.addLast(now);

            while (!d.times.isEmpty() && now - d.times.peekFirst() > 250L) {
               d.times.removeFirst();
            }

            int max = InfernoConfig.get().strictMode ? 8 : 12;
            if (d.times.size() > max) {
               this.flag(p, d.times.size() + " moves / 250ms");
               m.setback();
               d.times.clear();
            }
         }
      }

      private static final class Data {
         final Deque<Long> times = new ArrayDeque<>();
      }
   }
}
