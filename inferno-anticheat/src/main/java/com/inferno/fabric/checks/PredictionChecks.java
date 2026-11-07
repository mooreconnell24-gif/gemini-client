package com.inferno.fabric.checks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class PredictionChecks {
   private PredictionChecks() {
   }

   static double slipperiness(ServerLevel w, double x, double y, double z) {
      return w.getBlockState(BlockPos.containing(x, y - 0.500001, z)).getBlock().getFriction();
   }

   public static final class FlyPrediction extends Check {
      public FlyPrediction() {
         super("fly-prediction", "Fly (Prediction)", Check.Category.MOVEMENT, "Moving up or down in a way gravity doesn't allow (Fly, AirJump, LowHop, Glide).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         PredictionChecks.FlyPrediction.Last last = this.data(s, PredictionChecks.FlyPrediction.Last::new);
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky) {
            ServerLevel w = p.level();
            AABB now;
            boolean startGround = !w.noCollision(p, (now = p.getBoundingBox()).move(-m.dx, -m.dy, -m.dz).move(0.0, -0.03, 0.0));
            boolean endGround = !w.noCollision(p, now.move(0.0, -0.03, 0.0));
            boolean head = !w.noCollision(p, now.move(0.0, 0.03, 0.0));
            if (last.valid && !startGround && m.time - last.time < 250L && last.time > s.lastSetback) {
               double expected = last.head ? -0.0784 : (last.dy - 0.08) * 0.98;
               if (Math.abs(expected) < 0.003) {
                  expected = 0.0;
               }

               boolean bad;
               if (endGround) {
                  bad = m.dy < expected - 0.005;
               } else if (head) {
                  bad = m.dy > expected + 0.005;
               } else {
                  bad = Math.abs(m.dy - expected) > 0.005;
               }

               if (bad) {
                  if (this.buffer(s, 1.0) >= 2.0) {
                     this.buffer(s, -1.0);
                     this.flag(p, "moved " + round(m.dy) + " up/down, gravity allows " + round(expected));
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.1);
               }
            }

            last.dy = m.dy;
            last.head = head;
            last.time = m.time;
            last.valid = true;
         } else {
            last.valid = false;
         }
      }

      private static final class Last {
         double dy;
         boolean head;
         long time;
         boolean valid;
      }
   }

   public static final class SpeedPrediction extends Check {
      public SpeedPrediction() {
         super(
            "speed-prediction", "Speed (Prediction)", Check.Category.MOVEMENT, "Moving faster than Minecraft's movement physics allow (Speed, BHop, Strafe)."
         );
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         PredictionChecks.SpeedPrediction.Last last = this.data(s, PredictionChecks.SpeedPrediction.Last::new);
         ServerLevel w = p.level();
         AABB now;
         boolean startGround = !w.noCollision(p, (now = p.getBoundingBox()).move(-m.dx, -m.dy, -m.dz).move(0.0, -0.03, 0.0));
         double slip = PredictionChecks.slipperiness(w, m.fromX, m.fromY, m.fromZ);
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky && !m.soul) {
            if (last.valid && last.time > s.lastSetback && m.time - last.time < 1000L) {
               double friction = last.startGround ? last.slip * 0.91 : 0.91;
               boolean accelGround;
               double limit = last.horizontal * friction
                  + ((accelGround = startGround || last.clientGround) ? 0.0280800026 / (slip * slip * slip) : 0.026)
                  + (accelGround && m.dy > 0.0 ? 0.2 : 0.0)
                  + 0.01
                  + (w.getEntities(p, now.inflate(0.6)).isEmpty() ? 0.0 : 0.05);
               if (m.horizontal > limit) {
                  if (this.buffer(s, 1.0) >= 3.0) {
                     this.buffer(s, -2.0);
                     this.flag(p, round(m.horizontal) + " b/t, physics allow " + round(limit));
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.2);
               }
            }

            last.horizontal = m.horizontal;
            last.startGround = startGround;
            last.clientGround = m.clientGround;
            last.slip = slip;
            last.time = m.time;
            last.valid = true;
         } else {
            last.valid = false;
            last.clientGround = m.clientGround;
         }
      }

      private static final class Last {
         double horizontal;
         boolean startGround;
         boolean clientGround;
         double slip;
         long time;
         boolean valid;
      }
   }
}
