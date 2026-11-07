package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class NewChecks {
   private NewChecks() {
   }

   public static final class AimSmooth extends Check {
      public AimSmooth() {
         super("aim-smooth", "Aim (Smooth)", Check.Category.COMBAT, "Unnaturally consistent aim deltas (robotic aim assist).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.rotated && m.time - s.lastAttack <= 2500L) {
            Deque<Float> deltas = this.data(s, ArrayDeque::new);
            float mag = Math.abs(m.yawDelta) + Math.abs(m.pitchDelta);
            if (!(mag < 0.5F)) {
               deltas.addLast(mag);

               while (deltas.size() > 24) {
                  deltas.removeFirst();
               }

               if (deltas.size() >= 24) {
                  double mean = 0.0;

                  for (float v : deltas) {
                     mean += v;
                  }

                  mean /= deltas.size();
                  double var = 0.0;

                  for (float v : deltas) {
                     double d = v - mean;
                     var += d * d;
                  }

                  var /= deltas.size();
                  double maxVar = InfernoConfig.get().limit("aim-smooth.max-variance");
                  if (mean > 1.5 && var < maxVar) {
                     if (this.buffer(s, 1.0) >= 3.0) {
                        this.buffer(s, -2.0);
                        deltas.clear();
                        this.flag(p, "variance " + String.format(Locale.ROOT, "%.4f", var) + " mean " + round(mean));
                     }
                  } else {
                     this.decay(s, 0.2);
                  }
               }
            }
         }
      }
   }

   public static final class Blink extends Check {
      public Blink() {
         super("blink", "Blink", Check.Category.MOVEMENT, "Long gaps between move packets then a teleport.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         NewChecks.Blink.Data d = this.data(s, NewChecks.Blink.Data::new);
         long gap = d.last > 0L ? m.time - d.last : 0L;
         d.last = m.time;
         double maxGap = InfernoConfig.get().limit("blink.max-gap-ms");
         if (gap > maxGap && m.horizontal + Math.abs(m.dy) > 1.5 && !m.exempt && !m.special) {
            this.flag(p, "packet gap " + gap + "ms then moved " + round(m.horizontal));
            m.setback();
         }
      }

      private static final class Data {
         long last;
      }
   }

   public static final class BoatFly extends Check {
      public BoatFly() {
         super("boat-fly", "Boat Fly", Check.Category.MOVEMENT, "Boats staying airborne too long.");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         if (!(p.getVehicle() instanceof AbstractBoat boat)) {
            this.data(s, NewChecks.BoatFly.Data::new).airSince = 0L;
         } else {
            NewChecks.BoatFly.Data var8 = this.data(s, NewChecks.BoatFly.Data::new);
            boolean ground = boat.onGround() || boat.isInWater();
            if (ground) {
               var8.airSince = 0L;
            } else {
               if (var8.airSince == 0L) {
                  var8.airSince = System.currentTimeMillis();
               }

               long air = System.currentTimeMillis() - var8.airSince;
               if (air > InfernoConfig.get().limit("boat-fly.max-air-ms") && boat.getDeltaMovement().y >= -0.01 && this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -2.0);
                  this.flag(p, "boat air " + air + "ms");
               }
            }
         }
      }

      private static final class Data {
         long airSince;
      }
   }

   public static final class ElytraSpeed extends Check {
      public ElytraSpeed() {
         super("elytra-speed", "Elytra Speed", Check.Category.MOVEMENT, "Gliding faster than fireworks/physics allow.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (p.isFallFlying() && !m.exempt) {
            double speed = Math.sqrt(m.dx * m.dx + m.dy * m.dy + m.dz * m.dz);
            double max = InfernoConfig.get().limit("elytra-speed.max");
            if (speed > max) {
               if (this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -2.0);
                  this.flag(p, "elytra " + round(speed) + " b/t (max " + round(max) + ")");
               }
            } else {
               this.decay(s, 0.2);
            }
         }
      }
   }

   public static final class FastBreak extends Check {
      public FastBreak() {
         super("fast-break", "Fast Break", Check.Category.WORLD, "Breaking blocks faster than tool speed allows.");
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         if (s.digPos != null && s.digPos.equals(pos) && s.digTime > 0L) {
            long took = System.currentTimeMillis() - s.digTime;
            double min = InfernoConfig.get().limit("fastbreak.min-ms");
            if (took < min) {
               this.flag(p, "broke in " + took + "ms");
               return InfernoConfig.get().cancelBlocks;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static final class FastUse extends Check {
      public FastUse() {
         super("fast-use", "Fast Use", Check.Category.COMBAT, "Using bows/food/tridents faster than allowed.");
      }

      @Override
      public void onUseItem(ServerPlayer p, PlayerState s, InteractionHand hand) {
         ItemStack stack = p.getItemInHand(hand);
         if (!stack.isEmpty()) {
            String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
            boolean watch = id.contains("bow")
               || id.contains("crossbow")
               || id.contains("trident")
               || id.contains("golden_apple")
               || id.contains("enchanted_golden_apple")
               || id.contains("chorus")
               || id.contains("potion")
               || id.contains("cooked")
               || id.contains("bread")
               || id.contains("apple")
               || id.contains("stew")
               || id.contains("soup");
            if (watch) {
               NewChecks.FastUse.Data d = this.data(s, NewChecks.FastUse.Data::new);
               long now = System.currentTimeMillis();
               long gap = now - d.last;
               d.last = now;
               double minTicks = InfernoConfig.get().limit("fastuse.min-ticks");
               if (d.last <= 0L || !(gap < minTicks * 50.0) || gap <= 0L) {
                  this.decay(s, 0.25);
               } else if (this.buffer(s, 1.0) >= 4.0) {
                  this.buffer(s, -3.0);
                  this.flag(p, "use gap " + gap + "ms");
               }
            }
         }
      }

      private static final class Data {
         long last;
      }
   }

   public static final class GroundSpoof extends Check {
      public GroundSpoof() {
         super("ground-spoof", "Ground Spoof", Check.Category.MOVEMENT, "Client claims on-ground while airborne.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable()) {
            if (!m.clientGround || m.ground || !m.airborne() || !(Math.abs(m.dy) > 0.08)) {
               this.decay(s, 0.2);
            } else if (this.buffer(s, 1.0) >= 4.0) {
               this.buffer(s, -2.0);
               this.flag(p, "clientGround while airborne dy=" + round(m.dy));
               m.setback();
            }
         }
      }
   }

   public static final class InvalidMotion extends Check {
      public InvalidMotion() {
         super("invalid-motion", "Invalid Motion", Check.Category.MOVEMENT, "NaN/Inf or absurd position deltas.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         double max = InfernoConfig.get().limit("invalid-motion.max");
         if (Double.isNaN(m.toX)
            || Double.isNaN(m.toY)
            || Double.isNaN(m.toZ)
            || Double.isInfinite(m.toX)
            || Double.isInfinite(m.toY)
            || Double.isInfinite(m.toZ)
            || m.horizontal > max
            || Math.abs(m.dy) > max) {
            this.flag(p, "invalid delta h=" + round(m.horizontal) + " dy=" + round(m.dy));
            m.setback();
         }
      }
   }

   public static final class InvalidPitch extends Check {
      public InvalidPitch() {
         super("invalid-pitch", "Invalid Pitch", Check.Category.COMBAT, "Pitch outside vanilla look limits.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (!m.exempt) {
            if (m.pitch > 90.1F || m.pitch < -90.1F || Float.isNaN(m.pitch) || Float.isInfinite(m.pitch)) {
               this.flag(p, "pitch " + round(m.pitch));
               m.setback();
            }
         }
      }
   }

   public static final class InventoryClean extends Check {
      public InventoryClean() {
         super("inventory-clean", "Inventory Clean", Check.Category.WORLD, "Chest/inventory clicking too fast (stealer).");
      }

      @Override
      public void onClickSlot(ServerPlayer p, PlayerState s) {
         if (s.containerOpen) {
            NewChecks.InventoryClean.Data d = this.data(s, NewChecks.InventoryClean.Data::new);
            long now = System.currentTimeMillis();
            d.clicks.addLast(now);

            while (!d.clicks.isEmpty() && now - d.clicks.peekFirst() > 1000L) {
               d.clicks.removeFirst();
            }

            double max = InfernoConfig.get().limit("inventory-clean.max-cps");
            if (d.clicks.size() > max) {
               this.flag(p, d.clicks.size() + " container clicks/s");
               d.clicks.clear();
            }
         }
      }

      private static final class Data {
         final Deque<Long> clicks = new ArrayDeque<>();
      }
   }

   public static final class MultiAngle extends Check {
      public MultiAngle() {
         super("multi-angle", "Multi Angle", Check.Category.COMBAT, "Hitting multiple targets with impossible turn angles.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         NewChecks.MultiAngle.Data d = this.data(s, NewChecks.MultiAngle.Data::new);
         long now = System.currentTimeMillis();
         Vec3 eye = p.getEyePosition();
         double ang = CombatChecks.angleTo(eye, p.getYRot(), p.getXRot(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
         if (d.lastTarget != null && !d.lastTarget.equals(target.getStringUUID()) && now - d.lastAttack < 120L) {
            double turn = Math.abs(ang) + Math.abs(d.lastAng);
            double max = InfernoConfig.get().limit("multi-angle.max-deg");
            if (turn > max) {
               this.flag(p, "multi-target turn " + Math.round(turn) + "° in " + (now - d.lastAttack) + "ms");
               d.lastTarget = target.getStringUUID();
               d.lastAttack = now;
               d.lastAng = ang;
               return InfernoConfig.get().cancelHits;
            }
         }

         d.lastTarget = target.getStringUUID();
         d.lastAttack = now;
         d.lastAng = ang;
         return false;
      }

      private static final class Data {
         String lastTarget;
         long lastAttack;
         double lastAng;
      }
   }

   public static final class Regen extends Check {
      public Regen() {
         super("regen", "Regen", Check.Category.COMBAT, "Healing faster than vanilla regeneration allows.");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         if (!p.isCreative() && !p.isSpectator()) {
            NewChecks.Regen.Data d = this.data(s, NewChecks.Regen.Data::new);
            float hp = p.getHealth();
            long now = System.currentTimeMillis();
            if (d.lastHp > 0.0F && hp > d.lastHp + 0.1F && !p.getFoodData().needsFood()) {
               long gap = now - d.lastHeal;
               double min = InfernoConfig.get().limit("regen.min-interval-ms");
               if (d.lastHeal > 0L && gap < min && hp - d.lastHp >= 0.9F && this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -2.0);
                  this.flag(p, "healed +" + round(hp - d.lastHp) + " in " + gap + "ms");
               }

               d.lastHeal = now;
            }

            d.lastHp = hp;
         }
      }

      private static final class Data {
         float lastHp;
         long lastHeal;
      }
   }

   public static final class Strafe extends Check {
      public Strafe() {
         super("strafe", "Strafe", Check.Category.MOVEMENT, "Air control / acceleration beyond vanilla.");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.airborne()) {
            NewChecks.Strafe.Data d = this.data(s, NewChecks.Strafe.Data::new);
            if (d.lastTime > 0L && m.time - d.lastTime < 80L) {
               double accel = m.horizontal - d.lastH;
               double max = InfernoConfig.get().limit("strafe.max-air-accel");
               if (accel > max && m.horizontal > 0.35) {
                  if (this.buffer(s, 1.0) >= 5.0) {
                     this.buffer(s, -3.0);
                     this.flag(p, "air accel " + round(accel) + " (max " + round(max) + ")");
                     m.setback();
                  }
               } else {
                  this.decay(s, 0.15);
               }
            }

            d.lastH = m.horizontal;
            d.lastTime = m.time;
         }
      }

      private static final class Data {
         double lastH;
         long lastTime;
      }
   }

   public static final class VehicleSteer extends Check {
      public VehicleSteer() {
         super("vehicle-steer", "Vehicle Steer", Check.Category.MOVEMENT, "Impossible vehicle control while not riding.");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         NewChecks.VehicleSteer.Data d = this.data(s, NewChecks.VehicleSteer.Data::new);
         boolean riding = p.isPassenger();
         if (riding != d.wasRiding) {
            long now = System.currentTimeMillis();
            if (now - d.lastFlip < 80L) {
               d.flips++;
               if (d.flips >= 6) {
                  this.flag(p, "vehicle mount spam x" + d.flips);
                  d.flips = 0;
               }
            } else {
               d.flips = 0;
            }

            d.lastFlip = now;
         }

         d.wasRiding = riding;
      }

      private static final class Data {
         boolean wasRiding;
         long lastFlip;
         int flips;
      }
   }
}
