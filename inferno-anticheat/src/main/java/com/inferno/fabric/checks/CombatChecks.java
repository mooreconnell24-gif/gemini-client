package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatChecks {
   private CombatChecks() {
   }

   static double distanceToBox(Vec3 eye, AABB box) {
      double x = Math.max(box.minX, Math.min(box.maxX, eye.x));
      double y = Math.max(box.minY, Math.min(box.maxY, eye.y));
      double z = Math.max(box.minZ, Math.min(box.maxZ, eye.z));
      double dx = eye.x - x;
      double dy = eye.y - y;
      double dz = eye.z - z;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   static boolean rayHits(Vec3 eye, double[] dir, AABB box, double grow, double max) {
      double[] min = new double[]{box.minX - grow, box.minY - grow, box.minZ - grow};
      double[] mx = new double[]{box.maxX + grow, box.maxY + grow, box.maxZ + grow};
      double[] o = new double[]{eye.x, eye.y, eye.z};
      double tMin = 0.0;
      double tMax = max;

      for (int i = 0; i < 3; i++) {
         double t2;
         double t1;
         if (Math.abs(dir[i]) < 1.0E-9
            ? o[i] < min[i] || o[i] > mx[i]
            : (tMin = Math.max(tMin, Math.min(t1 = (min[i] - o[i]) / dir[i], t2 = (mx[i] - o[i]) / dir[i]))) > (tMax = Math.min(tMax, Math.max(t1, t2)))) {
            return false;
         }
      }

      return true;
   }

   static double angleTo(Vec3 eye, double yaw, double pitch, double tx, double ty, double tz) {
      double[] d = Check.direction(yaw, pitch);
      double vx = tx - eye.x;
      double vy = ty - eye.y;
      double vz = tz - eye.z;
      double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
      return len < 1.0E-6 ? 0.0 : Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, (d[0] * vx + d[1] * vy + d[2] * vz) / len))));
   }

   public static final class AimGcd extends Check {
      public AimGcd() {
         super("aim-gcd", "Aim (GCD)", Check.Category.COMBAT, "Rotations no real mouse could make (aimbot, aim assist, silent aim).");
      }

      static double gcd(double a, double b) {
         if ((a = Math.abs(a)) < (b = Math.abs(b))) {
            double t = a;
            a = b;
            b = t;
         }

         int guard = 0;

         while (b > 0.001 && guard++ < 100) {
            double t = a % b;
            a = b;
            b = t;
         }

         return a;
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.rotated && System.currentTimeMillis() - s.lastAttack <= 2000L && !p.isUsingItem()) {
            double delta = Math.abs(m.pitchDelta);
            if (!(Math.abs(m.pitch) > 88.0F) && !(delta < 0.05) && !(delta > 20.0)) {
               List samples = this.data(s, ArrayList::new);
               samples.add(delta);
               if (samples.size() >= 40) {
                  int bad = 0;

                  for (int i = 1; i < samples.size(); i++) {
                     if (gcd((Double)samples.get(i), (Double)samples.get(i - 1)) < 0.0085) {
                        bad++;
                     }
                  }

                  samples.clear();
                  double ratio = bad / 39.0;
                  if (ratio > 0.55) {
                     this.flag(p, Math.round(ratio * 100.0) + "% of rotations don't match a mouse");
                  }
               }
            }
         }
      }
   }

   public static final class AimSnap extends Check {
      public AimSnap() {
         super("aim-snap", "Aim (Snap)", Check.Category.COMBAT, "Instantly snapping onto a target right before hitting (KillAura, aimbot).");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (s.rotations.size() < 2) {
            return false;
         }

         Iterator<double[]> it = s.rotations.iterator();
         double[] now = it.next();
         double[] before = it.next();
         if (!(System.currentTimeMillis() - now[0] > 100.0) && !(now[0] - before[0] > 120.0)) {
            double snap = Math.abs(Move.wrap((float)(now[1] - before[1]))) + Math.abs(now[2] - before[2]);
            if (snap < 50.0) {
               return false;
            }

            Vec3 eye = p.getEyePosition();
            AABB b = target.getBoundingBox();
            double tx = (b.minX + b.maxX) / 2.0;
            double ty = b.minY + (b.maxY - b.minY) * 0.6;
            double tz = (b.minZ + b.maxZ) / 2.0;
            if (CombatChecks.angleTo(eye, before[1], before[2], tx, ty, tz) > 40.0
               && CombatChecks.angleTo(eye, now[1], now[2], tx, ty, tz) < 12.0
               && this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, "snapped " + Math.round(snap) + "° onto target in one tick");
            }

            return false;
         } else {
            return false;
         }
      }
   }

   public static final class AutoClicker extends Check {
      public AutoClicker() {
         super("autoclicker", "AutoClicker", Check.Category.COMBAT, "Attacking faster than a human can click.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         Deque<Long> hits = this.data(s, ArrayDeque::new);
         long now = System.currentTimeMillis();
         hits.addLast(now);

         while (!hits.isEmpty() && now - hits.peekFirst() > 1000L) {
            hits.removeFirst();
         }

         double maxCps = InfernoConfig.get().limit("autoclicker.max-cps");
         if (hits.size() > maxCps) {
            hits.clear();
            if (this.buffer(s, 1.0) >= 2.0) {
               this.buffer(s, -2.0);
               this.flag(p, "more than " + Math.round(maxCps) + " hits in a second");
            }
         } else if (hits.size() < 12) {
            this.decay(s, 0.05);
         }

         return false;
      }
   }

   public static final class Hitbox extends Check {
      public Hitbox() {
         super("hitbox", "KillAura (Hitbox)", Check.Category.COMBAT, "Hitting a target the player wasn't looking at (KillAura, Hitboxes).");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         Vec3 eye = p.getEyePosition();
         AABB box;
         if (CombatChecks.distanceToBox(eye, box = target.getBoundingBox()) < 0.8) {
            return false;
         }

         if (CombatChecks.rayHits(eye, direction(p.getYRot(), p.getXRot()), box, 0.4, 7.0)) {
            this.decay(s, 0.5);
            return false;
         }

         Iterator<double[]> it = s.rotations.iterator();

         for (int checked = 0; it.hasNext() && checked < 3; checked++) {
            double[] r = it.next();
            if (CombatChecks.rayHits(eye, direction(r[1], r[2]), box, 0.4, 7.0)) {
               this.decay(s, 0.5);
               return false;
            }
         }

         double need = InfernoConfig.get().strictMode ? 1.5 : 3.0;
         if (this.buffer(s, 1.0) >= need) {
            double angle = CombatChecks.angleTo(
               eye, p.getYRot(), p.getXRot(), (box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0, (box.minZ + box.maxZ) / 2.0
            );
            this.flag(p, "hit while looking " + Math.round(angle) + "° away");
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class KillAuraMulti extends Check {
      public KillAuraMulti() {
         super("killaura-multi", "KillAura (Multi)", Check.Category.COMBAT, "Hitting several targets at the same moment.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         long gap = System.currentTimeMillis() - s.lastAttack;
         if (s.lastTarget != null && !s.lastTarget.equals(target.getStringUUID()) && gap < 40L) {
            if (this.buffer(s, 1.0) >= 2.0) {
               this.flag(p, "switched targets in " + gap + "ms");
               return true;
            }
         } else if (gap > 500L) {
            this.decay(s, 0.1);
         }

         return false;
      }
   }

   public static final class Reach extends Check {
      public Reach() {
         super("reach", "Reach", Check.Category.COMBAT, "Hitting players or mobs from too far away.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         double reach = CombatChecks.distanceToBox(p.getEyePosition(), target.getBoundingBox());
         ItemStack weapon = p.getMainHandItem();
         double itemReach;
         if (weapon.has(DataComponents.ATTACK_RANGE)) {
            AttackRange range = p.getAttackRangeWith(weapon);
            itemReach = range.effectiveMaxRange(target) + range.hitboxMargin();
         } else {
            itemReach = TamperChecks.vanillaValue(p, Attributes.ENTITY_INTERACTION_RANGE);
         }

         double max = InfernoConfig.get().limit("reach.max-reach")
            + Math.max(0.0, itemReach - 3.0)
            + Math.min(1.0, target.getDeltaMovement().horizontalDistance());
         if (InfernoConfig.get().strictMode) {
            max = Math.min(max, InfernoConfig.get().limit("reach.max-reach") + 0.15);
         }

         if (reach > max) {
            double need = InfernoConfig.get().strictMode ? 1.0 : 2.0;
            if (this.buffer(s, 1.0) >= need) {
               this.flag(p, round(reach) + " blocks (limit " + round(max) + ")");
               return true;
            }
         } else {
            this.decay(s, 0.35);
         }

         return false;
      }
   }
}
