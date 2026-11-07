package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KnockbackChecks {
   private KnockbackChecks() {
   }

   public static final class Velocity extends Check {
      private static final double ACCEL = 0.13;
      private static final double JUMP_BOOST = 0.2;
      private static final double ENTITY_PUSH = 0.075;
      private static final double TOLERANCE = 0.03;
      private static final long GAP = 250L;
      private static final int PENDING = 0;
      private static final int CLEAN = 1;
      private static final int COUNTED = 2;
      private static final int DROPPED = 3;

      public Velocity() {
         super("velocity", "Velocity (Anti-KB)", Check.Category.COMBAT, "Taking less knockback than the server gave (Velocity, AntiKnockback).");
      }

      @Override
      public void onVelocity(ServerPlayer p, PlayerState s, Vec3 v) {
         KnockbackChecks.Velocity.Data d = this.data(s, KnockbackChecks.Velocity.Data::new);
         long now = System.currentTimeMillis();
         double base = Math.min(1500.0, Math.max(Math.min(p.connection.latency(), 1500), d.delay));
         KnockbackChecks.Velocity.Knock k = new KnockbackChecks.Velocity.Knock(now, v, base);
         ServerLevel w = p.level();
         AABB box = p.getBoundingBox();
         boolean judged = (k.judgeH || k.judgeV) && !CheckEngine.special(p) && !InfernoMod.ENGINE.serverLagging() && !unsafe(p, w, box);
         if (judged) {
            if (p.isShiftKeyDown() || blockedToward(p, w, box, k.dirX, k.dirZ)) {
               k.noH = true;
            }

            if (!w.noCollision(p, box.move(0.0, 0.6, 0.0))) {
               k.noV = true;
            }

            k.entities = crowd(p, w, box);
            judged = k.judgeable() && k.entities < 3;
         }

         ArrayList<KnockbackChecks.Velocity.Knock> keep = new ArrayList<>();

         for (KnockbackChecks.Velocity.Knock old : d.knocks) {
            if (now - old.time >= 150L && judged) {
               if (now - old.time < 2.0 * base + 500.0 && old.contester == null) {
                  old.contester = k;
               }

               keep.add(old);
            } else {
               old.state = 3;
            }
         }

         d.knocks.clear();
         d.knocks.addAll(keep);
         if (judged) {
            d.knocks.add(k);
            if (d.knocks.size() > 8) {
               d.knocks.remove(0).state = 3;
            }
         }
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         KnockbackChecks.Velocity.Data d = this.data(s, KnockbackChecks.Velocity.Data::new);
         if (!d.knocks.isEmpty()) {
            long now = m.time;
            ServerLevel w = p.level();
            AABB box = p.getBoundingBox();
            boolean unsure = m.special || m.liquid || m.climbing || m.sticky || unsafe(p, w, box) || InfernoMod.ENGINE.serverLagging();
            boolean sneaking = p.isShiftKeyDown();
            int crowd = crowd(p, w, box);
            int i = 0;

            while (i < d.knocks.size()) {
               KnockbackChecks.Velocity.Knock k = d.knocks.get(i);
               if (k.judgeH && !k.noH && (sneaking || blockedToward(p, w, box, k.dirX, k.dirZ))) {
                  k.noH = true;
               }

               if (m.headBlocked) {
                  k.noV = true;
               }

               k.entities = Math.max(k.entities, crowd);
               if (!unsure && s.velocityChange < k.time && s.lastSetback < k.time && k.entities < 3 && k.judgeable()) {
                  double push = m.dx * k.dirX + m.dz * k.dirZ;
                  k.maxPush = Math.max(k.maxPush, push);
                  k.maxDy = Math.max(k.maxDy, m.dy);
                  if (m.moved && k.shows(push, m.dy, now)) {
                     d.delay = Math.max(now - k.time, d.delay * 0.9);
                     this.decay(s, 1.0);

                     for (int j = 0; j < i; j++) {
                        d.knocks.get(j).state = 3;
                     }

                     k.state = 1;
                     d.knocks.subList(0, i + 1).clear();
                     i = 0;
                  } else if (now - k.lastEvent > 250L) {
                     k.state = 3;
                     d.knocks.remove(i);
                  } else {
                     k.lastEvent = now;
                     i++;
                  }
               } else {
                  k.state = 3;
                  d.knocks.remove(i);
               }
            }
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         KnockbackChecks.Velocity.Data d = this.data(s, KnockbackChecks.Velocity.Data::new);
         if (!d.knocks.isEmpty()) {
            long now = System.currentTimeMillis();
            int i = 0;

            while (i < d.knocks.size()) {
               KnockbackChecks.Velocity.Knock k = d.knocks.get(i);
               if (now < k.deadline) {
                  i++;
               } else if (now - k.deadline > 10000L) {
                  k.state = 3;
                  d.knocks.remove(i);
               } else {
                  if (k.contester != null) {
                     if (k.contester.state == 0) {
                        i++;
                        continue;
                     }

                     if (k.contester.state != 2) {
                        k.state = 3;
                        d.knocks.remove(i);
                        continue;
                     }
                  } else if (now - k.lastEvent > 250L) {
                     k.state = 3;
                     d.knocks.remove(i);
                     continue;
                  }

                  k.state = 2;
                  d.knocks.remove(i);
                  if (this.buffer(s, 1.0) >= 3.0) {
                     this.buffer(s, -1.0);
                     String detail = "ignored knockback: moved " + round(Math.max(0.0, k.maxPush)) + " of " + round(k.h) + " away";
                     if (k.judgeV && !k.noV) {
                        detail = detail + ", rose " + round(Math.max(0.0, k.maxDy)) + " of " + round(k.vy);
                     }

                     this.flag(p, detail);
                  }
               }
            }
         }
      }

      private static boolean unsafe(ServerPlayer p, ServerLevel w, AABB box) {
         return p.isInWater()
            || p.isInLava()
            || p.onClimbable()
            || CheckEngine.blocksNear(w, box.inflate(0.1), "water", "lava", "bubble", "cobweb", "powder_snow", "honey", "sweet_berry", "scaffolding", "slime")
            || CheckEngine.blocksNear(w, box.expandTowards(0.0, -1.0, 0.0), "slime", "honey", "soul");
      }

      private static boolean blockedToward(ServerPlayer p, ServerLevel w, AABB box, double dx, double dz) {
         AABB body = new AABB(box.minX, box.minY + 0.1, box.minZ, box.maxX, box.maxY - 0.1, box.maxZ);
         return !w.noCollision(p, body.expandTowards(dx, 0.0, dz));
      }

      private static int crowd(ServerPlayer p, ServerLevel w, AABB box) {
         int n = 0;

         for (Entity e : w.getEntities(p, box.inflate(0.3))) {
            if (e.isPushable()) {
               n++;
            }
         }

         return n;
      }

      private static final class Data {
         final List<KnockbackChecks.Velocity.Knock> knocks = new ArrayList<>();
         double delay;
      }

      private static final class Knock {
         final long time;
         final long deadline;
         final long expected;
         final double vy;
         final double h;
         final double dirX;
         final double dirZ;
         final boolean judgeH;
         final boolean judgeV;
         boolean noH;
         boolean noV;
         int entities;
         double maxPush = -10.0;
         double maxDy = -10.0;
         long lastEvent;
         KnockbackChecks.Velocity.Knock contester;
         int state = 0;

         Knock(long now, Vec3 v, double base) {
            this.time = now;
            this.deadline = now + 2500L + (long)(2.0 * base);
            this.expected = now + (long)(2.0 * base) + 1000L;
            this.vy = v.y;
            this.h = Math.sqrt(v.x * v.x + v.z * v.z);
            this.dirX = this.h > 0.0 ? v.x / this.h : 0.0;
            this.dirZ = this.h > 0.0 ? v.z / this.h : 0.0;
            this.judgeH = this.h >= 0.3;
            this.judgeV = this.vy - 0.03 >= 0.15;
            this.lastEvent = now;
         }

         boolean judgeable() {
            return this.judgeH && !this.noH || this.judgeV && !this.noV;
         }

         boolean shows(double push, double dy, long now) {
            return this.judgeH
                  && !this.noH
                  && push < this.h - 0.13 - ((dy > this.vy + 0.01 || this.vy >= 0.41) && now <= this.expected ? 0.2 : 0.0) - 0.075 * this.entities - 0.03
               ? false
               : !this.judgeV || this.noV || dy >= this.vy - 0.03;
         }
      }
   }
}
