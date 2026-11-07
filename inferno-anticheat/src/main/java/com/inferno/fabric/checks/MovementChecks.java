package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class MovementChecks {
   private MovementChecks() {
   }

   public static final class Fly extends Check {
      public Fly() {
         super("fly", "Fly", Check.Category.MOVEMENT, "Hovering, floating still or flying upward in mid-air.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.Fly.Air a = this.data(s, MovementChecks.Fly.Air::new);
         if (m.checkable() && !m.ground && !m.liquid && !m.climbing && !m.sticky) {
            if (m.dy > 0.08 && s.airTicks > 2) {
               if (++a.ascend >= (InfernoConfig.get().strictMode ? 2 : 4)) {
                  a.ascend = 0;
                  this.flag(p, "rising in mid-air (" + round(m.dy) + " b/t)");
                  m.setback();
                  return;
               }
            } else if (m.dy <= 0.0) {
               a.ascend = 0;
            }

            if (m.dy > -0.075 && m.dy <= -0.02) {
               if (++a.slowFall >= (InfernoConfig.get().strictMode ? 3 : 5)) {
                  a.slowFall = 0;
                  this.flag(p, "gliding down slowly (" + round(m.dy) + " b/t)");
                  m.setback();
                  return;
               }
            } else if (m.moved) {
               a.slowFall = 0;
            }

            if (Math.abs(m.dy) < 0.03 && m.horizontal < 0.05 && s.airTicks > 4) {
               if (++a.hoverTicks >= 3) {
                  a.hoverTicks = 0;
                  this.flag(p, "hovering in place");
                  m.setback();
                  return;
               }
            } else {
               a.hoverTicks = 0;
            }

            if (m.dy < -0.03) {
               a.notFallingSince = 0L;
            } else {
               if (a.notFallingSince == 0L) {
                  a.notFallingSince = m.time;
               } else {
                  double maxAir = InfernoConfig.get().limit("fly.max-air-ms");
                  if (InfernoConfig.get().strictMode) {
                     maxAir = Math.min(maxAir, 400.0);
                  }

                  if (m.time - a.notFallingSince > maxAir) {
                     a.notFallingSince = 0L;
                     this.flag(p, "in the air without falling for too long");
                     m.setback();
                  }
               }
            }
         } else {
            a.notFallingSince = 0L;
            a.slowFall = 0;
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         long now = System.currentTimeMillis();
         if (Enforcement.locked(s)) {
            Enforcement.snap(p, s);
         } else if (now - s.lastPositionChange >= 1500L && !s.exempt() && now - s.joinTime >= 3000L && !CheckEngine.special(p)) {
            ServerLevel w = p.level();
            AABB box;
            if (w.noCollision(p, (box = p.getBoundingBox()).expandTowards(0.0, -1.01, 0.0))
               && !p.isInWater()
               && !p.isInLava()
               && !p.onClimbable()
               && !CheckEngine.blocksNear(w, box.inflate(0.1), "cobweb", "powder_snow", "scaffolding", "honey")) {
               MovementChecks.Fly.Air a = this.data(s, MovementChecks.Fly.Air::new);
               if (now - a.lastHover >= 1200L) {
                  a.lastHover = now;
                  this.flag(p, "floating still in mid-air");
                  if (!Double.isNaN(s.safeX) && InfernoConfig.get().setbacks) {
                     p.connection.teleport(s.safeX, s.safeY, s.safeZ, p.getYRot(), p.getXRot());
                     p.setDeltaMovement(0.0, 0.0, 0.0);
                     s.lastSetback = now;
                     Enforcement.onMovementViolation(p, s, "hover");
                  }
               }
            }
         }
      }

      private static final class Air {
         long notFallingSince;
         long lastHover;
         int slowFall;
         int ascend;
         int hoverTicks;
      }
   }

   public static final class FlyGravity extends Check {
      public FlyGravity() {
         super("fly-gravity", "Fly (Gravity)", Check.Category.MOVEMENT, "Not falling the way gravity says they should (Fly, Glide, AirJump).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.FlyGravity.Last last = this.data(s, MovementChecks.FlyGravity.Last::new);
         if (m.airborne() && !m.headBlocked) {
            boolean consecutive = last.valid && m.time - last.time < 80L && last.time > s.lastSetback;
            double expected = (last.dy - 0.08) * 0.98;
            if (consecutive && Math.abs(m.dy) > 0.05 && Math.abs(last.dy) > 0.05) {
               double tol = InfernoConfig.get().strictMode ? 0.012 : 0.03;
               if (Math.abs(m.dy - expected) > tol) {
                  if (this.buffer(s, 1.0) >= (InfernoConfig.get().strictMode ? 1.5 : 3.0)) {
                     this.buffer(s, -1.0);
                     this.flag(
                        p, (m.dy > expected ? "falling too slowly" : "falling too fast") + " (expected " + round(expected) + ", got " + round(m.dy) + ")"
                     );
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.35);
               }
            }

            last.dy = m.dy;
            last.time = m.time;
            last.valid = true;
         } else {
            last.valid = false;
         }
      }

      private static final class Last {
         double dy;
         long time;
         boolean valid;
      }
   }

   public static final class Jesus extends Check {
      private static boolean liquidBlock(ServerLevel world, BlockPos pos) {
         BlockState state = world.getBlockState(pos);
         return !state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
      }

      public Jesus() {
         super("jesus", "Jesus", Check.Category.MOVEMENT, "Walking on water or lava.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.Jesus.Walk w = this.data(s, MovementChecks.Jesus.Walk::new);
         ServerLevel world = p.level();
         if (m.checkable() && !m.ground && !(m.horizontal < 0.08) && !(Math.abs(m.dy) > 0.001)) {
            BlockPos feet = BlockPos.containing(m.toX, m.toY + 0.05, m.toZ);
            BlockPos under = BlockPos.containing(m.toX, m.toY - 0.1, m.toZ);
            boolean feetLiquid = liquidBlock(world, feet);
            boolean underLiquid = liquidBlock(world, under);
            if (!feetLiquid && underLiquid && !p.isInWater()) {
               if (++w.ticks > 8) {
                  w.ticks = 0;
                  this.flag(p, "walking on liquid");
                  m.setback();
               }
            } else {
               w.ticks = 0;
            }
         } else {
            w.ticks = 0;
         }
      }

      private static final class Walk {
         int ticks;
      }
   }

   public static final class NoFall extends Check {
      public NoFall() {
         super("nofall", "NoFall", Check.Category.MOVEMENT, "Pretending to be on the ground mid-air to avoid fall damage.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !m.ground && !m.lastGround && m.clientGround && !m.liquid && !m.climbing && !m.sticky) {
            if (this.buffer(s, 1.0) >= 3.0) {
               this.buffer(s, -3.0);
               this.flag(p, "claimed to be on the ground in mid-air");
               m.setback();
            }
         } else {
            this.decay(s, 0.25);
         }
      }
   }

   public static final class Speed extends Check {
      public Speed() {
         super("speed", "Speed (Limit)", Check.Category.MOVEMENT, "Moving further in one tick than is possible.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable()) {
            double limit = m.ice ? 1.45 : (m.soul ? 1.1 : (m.liquid ? 1.0 : InfernoConfig.get().limit("speed.max-speed")));
            if (m.horizontal > limit) {
               double need = InfernoConfig.get().strictMode ? 1.5 : 3.0;
               if (this.buffer(s, 1.0) >= need) {
                  this.buffer(s, -1.0);
                  this.flag(p, round(m.horizontal) + " b/t (limit " + round(limit) + ")");
                  m.setback();
               }
            } else {
               this.decay(s, 0.2);
            }
         }
      }
   }

   public static final class SpeedAir extends Check {
      public SpeedAir() {
         super("speed-air", "Speed (Air)", Check.Category.MOVEMENT, "Speeding up or turning in mid-air more than possible (Strafe, BHop).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.SpeedAir.Last last = this.data(s, MovementChecks.SpeedAir.Last::new);
         if (!m.airborne()) {
            last.valid = false;
         } else {
            if (last.valid && m.time - last.time < 80L && last.time > s.lastSetback && m.horizontal > 0.1) {
               double slack = InfernoConfig.get().strictMode ? 0.004 : 0.01;
               double limit = last.horizontal * 0.91 + 0.026 + slack;
               if (m.horizontal > limit) {
                  if (this.buffer(s, 1.0) >= (InfernoConfig.get().strictMode ? 1.5 : 3.0)) {
                     this.buffer(s, -1.0);
                     this.flag(p, round(m.horizontal) + " b/t in mid-air (max " + round(limit) + ")");
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.25);
               }
            }

            last.horizontal = m.horizontal;
            last.time = m.time;
            last.valid = true;
         }
      }

      private static final class Last {
         double horizontal;
         long time;
         boolean valid;
      }
   }

   public static final class Spider extends Check {
      public Spider() {
         super("spider", "Spider", Check.Category.MOVEMENT, "Climbing walls without a ladder.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.Spider.Climb c = this.data(s, MovementChecks.Spider.Climb::new);
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky && !(m.dy <= 0.01)) {
            if (++c.ticks > 8) {
               c.ticks = 0;
               this.flag(p, "kept rising for too long");
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

   public static final class Step extends Check {
      public Step() {
         super("step", "Step", Check.Category.MOVEMENT, "Stepping up full blocks instantly (Step).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !m.liquid && !m.climbing && m.lastGround && m.dy > 0.62 && m.dy < 4.0) {
            this.flag(p, "went up " + round(m.dy) + " blocks in one move");
            m.setback();
         }
      }
   }

   public static final class Timer extends Check {
      public Timer() {
         super("timer", "Timer", Check.Category.MOVEMENT, "Speeding up the game clock (Timer).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         MovementChecks.Timer.Balance b = this.data(s, MovementChecks.Timer.Balance::new);
         long now = m.time;
         ServerTickRateManager rate = p.level().getServer().tickRateManager();
         boolean customRate = rate.isSprinting() || rate.isFrozen() || rate.isSteppingForward() || Math.abs(rate.tickrate() - 20.0F) > 0.01F;
         if (b.last != 0L && !m.special && m.time - s.joinTime >= 5000L && now - s.lastSetback >= 1000L && !InfernoMod.ENGINE.serverLagging() && !customRate) {
            b.ahead = b.ahead + (50L - (now - b.last));
            b.last = now;
            b.ahead = Math.max(b.ahead, -1000.0);
            if (b.ahead > InfernoConfig.get().limit("timer.max-ahead-ms")) {
               if (this.buffer(s, 1.0) >= 2.0) {
                  this.flag(p, Math.round(b.ahead) + "ms ahead of real time");
                  m.setback();
               }

               b.ahead = 0.0;
            } else {
               this.decay(s, 0.002);
            }
         } else {
            b.last = now;
            b.ahead = 0.0;
         }
      }

      private static final class Balance {
         long last;
         double ahead;
      }
   }
}
