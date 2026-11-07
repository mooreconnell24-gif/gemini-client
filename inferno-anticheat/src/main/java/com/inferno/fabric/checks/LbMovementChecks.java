package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;

/**
 * Movement checks added for LiquidBounce coverage. Most of them compare what the player did with the
 * keys the client says are held (ServerboundPlayerInputPacket), which a vanilla client always reports
 * truthfully and which LiquidBounce's Speed/Strafe/Sprint/SafeWalk/InventoryMove modules don't line up
 * with their movement.
 */
public final class LbMovementChecks {
   private LbMovementChecks() {
   }

   static boolean survival(ServerPlayer p) {
      return !p.isCreative() && !p.isSpectator();
   }

   static boolean movementKey(Input in) {
      return in.forward() || in.backward() || in.left() || in.right();
   }

   /** Box the player had before this move. */
   static AABB startBox(ServerPlayer p, Move m) {
      return p.getBoundingBox().move(-m.dx, -m.dy, -m.dz);
   }

   /** Really standing on something (3 hundredths of a block), unlike Move#ground which allows 1 block. */
   static boolean touching(ServerLevel w, ServerPlayer p, AABB box) {
      return !w.noCollision(p, box.move(0.0, -0.03, 0.0));
   }

   /** This move left the ground upwards like a jump: was standing, now airborne, rising 0.2-0.6. */
   static boolean jumpLaunch(ServerPlayer p, Move m) {
      ServerLevel w = p.level();
      return m.dy > 0.2 && m.dy < 0.6 && touching(w, p, startBox(p, m)) && !touching(w, p, p.getBoundingBox());
   }

   /** Ordinary terrain: not water, ladders, cobwebs/honey/powder snow or ice. */
   static boolean plainTerrain(Move m) {
      return !m.liquid && !m.climbing && !m.sticky && !m.ice;
   }

   public static final class HighJump extends Check {
      public HighJump() {
         super("high-jump", "HighJump", Check.Category.MOVEMENT, "Rising higher above the take-off point than a vanilla jump reaches (HighJump, AirJump).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (m.moved) {
            if (!m.checkable() || m.liquid || m.climbing || m.sticky || s.recentlyPushed(1500L) || p.isPassenger()) {
               d.valid = false;
            } else if (touching(p.level(), p, p.getBoundingBox())) {
               d.groundY = m.toY;
               d.valid = true;
            } else if (d.valid) {
               double rise = m.toY - d.groundY;
               double limit = InfernoConfig.get().limit("high-jump.max-height");
               if (rise > limit) {
                  d.valid = false;
                  this.flag(p, "rose " + round(rise) + " blocks above where they jumped (a jump reaches " + round(Detect.jumpApex(Detect.JUMP_IMPULSE)) + ")");
                  m.setback();
               }
            }
         }
      }

      private static final class Data {
         double groundY;
         boolean valid;
      }
   }

   public static final class ReverseStep extends Check {
      public ReverseStep() {
         super("reverse-step", "ReverseStep", Check.Category.MOVEMENT, "Dropping off a ledge faster than gravity allows on the first tick (ReverseStep, FastFall).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky && !s.recentlyPushed(1000L) && !p.isPassenger()) {
            // From standing, the first tick of a fall moves (0 - 0.08) * 0.98 = -0.078 blocks.
            if (m.dy < -0.6 && touching(p.level(), p, startBox(p, m))) {
               if (this.buffer(s, 1.0) >= 1.5) {
                  this.buffer(s, -1.0);
                  this.flag(p, "dropped " + round(-m.dy) + " blocks in the first tick of a fall (vanilla: " + round(-Detect.fallSpeedAfter(1)) + ")");
                  m.setback();
               }
            } else {
               this.decay(s, 0.01);
            }
         }
      }
   }

   public static final class JumpDelay extends Check {
      public JumpDelay() {
         super("jump-delay", "NoJumpDelay", Check.Category.MOVEMENT, "Jumping again sooner than the 10-tick delay while the jump key stays held (NoJumpDelay, BHop).");
      }

      @Override
      public void onInput(ServerPlayer p, PlayerState s, Input before, Input now) {
         if (!now.jump()) {
            this.data(s, Data::new).released = true;
         }
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky && !s.recentlyPushed(1000L) && jumpLaunch(p, m)) {
            Data d = this.data(s, Data::new);
            long gap = s.clientTicks - d.lastJumpTick;
            if (d.lastJumpTick > 0L && !d.released && gap < 9L) {
               if (this.buffer(s, 1.0) >= 2.0) {
                  this.buffer(s, -1.0);
                  this.flag(p, "jumped again " + gap + " ticks after the last jump with jump held (vanilla waits 10)");
               }
            } else {
               this.decay(s, 0.25);
            }

            d.lastJumpTick = s.clientTicks;
            d.released = !p.getLastClientInput().jump();
         }
      }

      private static final class Data {
         long lastJumpTick;
         boolean released = true;
      }
   }

   public static final class JumpInput extends Check {
      public JumpInput() {
         super("jump-input", "Jump (No Key)", Check.Category.MOVEMENT, "Jumping off the ground without the jump key held (Speed/BHop auto-jump, Parkour, LongJump).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         if (m.checkable() && !m.liquid && !m.climbing && !m.sticky && !s.recentlyPushed(1500L) && !p.isPassenger() && jumpLaunch(p, m)) {
            // A vanilla jump, including Auto-Jump (ClientInput#makeJump), always reports jump=true.
            boolean key = p.getLastClientInput().jump() || m.time - s.lastJumpInput < 200L;
            if (key) {
               this.decay(s, 0.5);
            } else if (this.buffer(s, 1.0) >= 3.0) {
               this.buffer(s, -1.0);
               this.flag(p, "jumped (" + round(m.dy) + " up) without pressing jump");
            }
         }
      }
   }

   public static final class InputMove extends Check {
      public InputMove() {
         super("input-move", "Move (No Keys)", Check.Category.MOVEMENT, "Speeding up on the ground with no movement key held (Speed, TargetStrafe, AutoDodge, Freeze bypasses).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         ServerLevel w = p.level();
         AABB box = p.getBoundingBox();
         if (!m.checkable() || !plainTerrain(m) || s.recentlyPushed(1000L) || p.isPassenger() || !touching(w, p, box) || !touching(w, p, startBox(p, m))) {
            d.valid = false;
            d.count = 0;
         } else {
            boolean keys = movementKey(p.getLastClientInput()) || m.time - s.lastInputChange < 150L;
            boolean crowded = !w.getEntities(p, box.inflate(0.3)).isEmpty();
            if (!keys && d.valid && !crowded) {
               double slip = PredictionChecks.slipperiness(w, m.fromX, m.fromY, m.fromZ);
               double coast = Detect.coastSpeed(d.lastH, slip) + 0.02;
               if (m.horizontal > 0.08 && m.horizontal > coast) {
                  if (++d.count >= 4) {
                     d.count = 0;
                     this.flag(p, "moving " + round(m.horizontal) + " b/t on the ground with no movement key held (coasting allows " + round(coast) + ")");
                     m.setback();
                  }
               } else {
                  d.count = 0;
               }
            } else {
               d.count = 0;
            }

            d.lastH = m.horizontal;
            d.valid = true;
         }
      }

      private static final class Data {
         double lastH;
         boolean valid;
         int count;
      }
   }

   public static final class SprintInvalid extends Check {
      public SprintInvalid() {
         super("sprint-invalid", "Sprint (Invalid)", Check.Category.MOVEMENT, "Sprinting without holding forward, or while too hungry to sprint (Sprint omni/ignore hunger, AntiHunger).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (m.checkable() && p.isSprinting() && !p.isPassenger() && !p.isInWater() && !p.isSwimming() && !p.getAbilities().flying && !(m.horizontal < 0.1)) {
            Input in = p.getLastClientInput();
            if (!(in.forward() && !in.backward()) && m.time - s.lastInputChange > 100L) {
               if (++d.noForward >= 4) {
                  d.noForward = 0;
                  this.flag(p, "sprinting while " + (in.backward() ? "holding back" : "not holding forward"));
               }
            } else {
               d.noForward = 0;
            }

            int food = p.getFoodData().getFoodLevel();
            // The client learns about food changes one ping later, so allow that long before judging.
            int grace = 10 + Math.min(p.connection.latency(), 1000) / 50;
            if (food <= 6) {
               if (++d.hungry >= grace) {
                  d.hungry = 0;
                  this.flag(p, "sprinting with " + food + " food (vanilla needs more than 6)");
               }
            } else {
               d.hungry = 0;
            }
         } else {
            d.noForward = 0;
            d.hungry = 0;
         }
      }

      private static final class Data {
         int noForward;
         int hungry;
      }
   }

   public static final class SneakSpeed extends Check {
      public SneakSpeed() {
         super("sneak-speed", "Sneak (NoSlow)", Check.Category.MOVEMENT, "Walking at full speed while the server sees the player sneaking (Sneak, NoSlow).");
      }

      @Override
      public void onInput(ServerPlayer p, PlayerState s, Input before, Input now) {
         if (now.shift() && !before.shift()) {
            this.data(s, Data::new).shiftSince = System.currentTimeMillis();
         }
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         ServerLevel w = p.level();
         if (m.checkable()
            && p.isShiftKeyDown()
            && plainTerrain(m)
            && !p.isPassenger()
            && !s.recentlyPushed(1000L)
            && m.time - d.shiftSince > 400L
            && touching(w, p, p.getBoundingBox())
            && touching(w, p, startBox(p, m))) {
            double limit = Detect.sneakGroundSpeed(p.getAttributeValue(Attributes.SNEAKING_SPEED), p.isSprinting()) + 0.05;
            if (m.horizontal > limit) {
               if (++d.count >= 6) {
                  d.count = 0;
                  this.flag(p, round(m.horizontal) + " b/t while sneaking (limit " + round(limit) + ")");
                  m.setback();
               }
            } else {
               d.count = 0;
            }
         } else {
            d.count = 0;
         }
      }

      private static final class Data {
         long shiftSince;
         int count;
      }
   }

   public static final class SafeWalk extends Check {
      public SafeWalk() {
         super("safe-walk", "SafeWalk", Check.Category.MOVEMENT, "Stopping dead at a block edge while still holding forward and not sneaking (SafeWalk, Scaffold safe-walk).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         d.tickH += m.horizontal;
         if (m.horizontal > 0.01) {
            d.dirX = m.dx / m.horizontal;
            d.dirZ = m.dz / m.horizontal;
         }

         if (m.exempt || m.special || !plainTerrain(m)) {
            d.lastTickH = 0.0;
            d.lastTickH2 = 0.0;
         }
      }

      @Override
      public void onClientTick(ServerPlayer p, PlayerState s, ClientTick tick) {
         Data d = this.data(s, Data::new);
         double now = d.tickH;
         // The stop can land mid-tick (part of a step, then nothing), so compare with the faster of the
         // two previous ticks. Holding forward, vanilla keeps its speed unless something blocks it.
         double before = Math.max(d.lastTickH, d.lastTickH2);
         d.lastTickH2 = d.lastTickH;
         d.lastTickH = now;
         d.tickH = 0.0;
         Input in = p.getLastClientInput();
         ServerLevel w = p.level();
         AABB box = p.getBoundingBox();
         if (survival(p)
            && before >= 0.12
            && now < before * 0.35
            && in.forward()
            && !in.backward()
            && !p.isShiftKeyDown()
            && !p.isUsingItem()
            && !p.isPassenger()
            && !CheckEngine.special(p)
            && !s.recentlyPushed(1000L)
            && !s.exempt()
            && !InfernoMod.ENGINE.serverLagging()
            && System.currentTimeMillis() - s.lastInputChange > 150L
            && touching(w, p, box)) {
            AABB ahead = box.move(d.dirX * 0.3, 0.0, d.dirZ * 0.3);
            boolean wall = !w.noCollision(p, ahead.move(0.0, 0.05, 0.0));
            boolean drop = w.noCollision(p, ahead.move(0.0, -0.6, 0.0)) && w.noCollision(p, box.move(d.dirX * 0.6, -0.6, d.dirZ * 0.6));
            if (!wall && drop && !CheckEngine.blocksNear(w, box.inflate(0.2), "cobweb", "sweet_berry", "powder_snow", "honey", "soul")) {
               if (this.buffer(s, 1.0) >= 4.0) {
                  this.buffer(s, -2.0);
                  this.flag(p, "stopped dead at a ledge (" + round(before) + " -> " + round(now) + " b/t) while holding forward");
               }

               return;
            }
         }

         if (before >= 0.12) {
            this.decay(s, 0.01);
         }
      }

      private static final class Data {
         double tickH;
         double lastTickH;
         double lastTickH2;
         double dirX;
         double dirZ;
      }
   }

   public static final class LiquidSpeed extends Check {
      public LiquidSpeed() {
         super("liquid-speed", "Speed (Liquid)", Check.Category.MOVEMENT, "Swimming faster than water allows (LiquidWalk swim modes, Speed in water).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (m.checkable() && (p.isInWater() || p.isInLava()) && !p.isPassenger() && !m.sticky && !s.recentlyPushed(1000L)) {
            double efficiency = Math.max(0.0, Math.min(1.0, p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)));
            double limit = InfernoConfig.get().limit("liquid-speed.max") + 0.35 * efficiency;
            if (m.horizontal > limit) {
               if (++d.count >= 6) {
                  d.count = 0;
                  this.flag(p, round(m.horizontal) + " b/t in water (limit " + round(limit) + ")");
                  m.setback();
               }
            } else {
               d.count = 0;
            }
         } else {
            d.count = 0;
         }
      }

      private static final class Data {
         int count;
      }
   }

   public static final class AntiLevitation extends Check {
      public AntiLevitation() {
         super("anti-levitation", "AntiLevitation", Check.Category.MOVEMENT, "Not floating up while Levitation is active and there is room above (AntiLevitation).");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         MobEffectInstance lev = p.getEffect(MobEffects.LEVITATION);
         long now = System.currentTimeMillis();
         if (lev == null || lev.getAmplifier() < 0 || lev.getAmplifier() > 127) {
            d.since = 0L;
            d.count = 0;
         } else {
            if (d.since == 0L) {
               d.since = now;
            }

            ServerLevel w = p.level();
            AABB box = p.getBoundingBox();
            boolean judge = survival(p)
               && now - d.since > 1000L + Math.min(p.connection.latency(), 1000)
               && !s.recentlyPushed(1000L)
               && now - s.joinTime > 3000L
               && !InfernoMod.ENGINE.serverLagging()
               && !p.isPassenger()
               && !p.isFallFlying()
               && !p.isInWater()
               && !p.isInLava()
               && !p.onClimbable()
               && w.noCollision(p, box.move(0.0, 0.6, 0.0))
               && !CheckEngine.blocksNear(w, box.inflate(0.1), "cobweb", "powder_snow", "honey", "scaffolding", "sweet_berry");
            boolean rising = s.lastDy > 0.01 && now - s.lastPositionChange < 150L;
            if (judge && !rising) {
               if (++d.count >= 30 && now - d.lastFlag > 3000L) {
                  d.count = 0;
                  d.lastFlag = now;
                  this.flag(p, "not rising for 1.5 s with Levitation " + (lev.getAmplifier() + 1));
               }
            } else {
               d.count = 0;
            }
         }
      }

      private static final class Data {
         long since;
         long lastFlag;
         int count;
      }
   }

   public static final class ElytraFly extends Check {
      public ElytraFly() {
         super("elytra-fly", "ElytraFly", Check.Category.MOVEMENT, "Hovering, flying level without losing speed, or climbing for too long on an elytra without rockets (ElytraFly, ElytraRecast).");
      }

      private static boolean judge(ServerPlayer p, PlayerState s) {
         long now = System.currentTimeMillis();
         return p.isFallFlying()
            && survival(p)
            && !s.recentlyPushed(2000L)
            && now - s.lastFireworkUse > 4000L
            && now - s.joinTime > 3000L
            && !InfernoMod.ENGINE.serverLagging()
            && !p.isInWater()
            && !p.isInLava()
            && !p.isAutoSpinAttack();
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (!m.moved) {
            return;
         }

         if (!judge(p, s)) {
            d.level = 0;
            d.climb = 0;
            d.climbHeight = 0.0;
            return;
         }

         if (Math.abs(m.dy) < 0.005 && m.horizontal > 0.3) {
            if (d.level == 0) {
               d.levelStartH = m.horizontal;
            }

            if (++d.level >= 20 && m.horizontal >= d.levelStartH * 0.97) {
               d.level = 0;
               this.flag(p, "flew level for a second at " + round(m.horizontal) + " b/t without slowing down");
               m.setback();
               return;
            }
         } else {
            d.level = 0;
         }

         if (m.dy > 0.0) {
            d.climb++;
            d.climbHeight += m.dy;
            // Pulling up after the fastest dive (about 3.5 b/t) trades speed for at most ~76 blocks.
            if (d.climb >= 140 || d.climbHeight > 90.0) {
               this.flag(p, "climbed " + Math.round(d.climbHeight) + " blocks over " + d.climb + " ticks without a rocket");
               d.climb = 0;
               d.climbHeight = 0.0;
               m.setback();
            }
         } else if (m.dy < -0.05) {
            d.climb = 0;
            d.climbHeight = 0.0;
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         long now = System.currentTimeMillis();
         if (judge(p, s) && !p.onGround() && now - s.lastPositionChange >= 1000L && now - d.lastHover > 2000L) {
            d.lastHover = now;
            this.flag(p, "hanging still in the air while gliding");
         }
      }

      private static final class Data {
         int level;
         double levelStartH;
         int climb;
         double climbHeight;
         long lastHover;
      }
   }

   public static final class RiptideSpeed extends Check {
      public RiptideSpeed() {
         super("riptide-speed", "TridentBoost", Check.Category.MOVEMENT, "Riptide launches faster than the enchantment gives (TridentBoost).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Data d = this.data(s, Data::new);
         if (p.isAutoSpinAttack() && survival(p) && !s.recentlyPushed(1000L) && !InfernoMod.ENGINE.serverLagging()) {
            // Riptide III pushes 3 b/t (3.0 * (1 + level) / 4); the limit leaves room for speed already held.
            double limit = InfernoConfig.get().limit("riptide.max-speed");
            if (m.horizontal > limit) {
               if (++d.count >= 2) {
                  d.count = 0;
                  this.flag(p, "riptide at " + round(m.horizontal) + " b/t (limit " + round(limit) + ")");
               }
            } else {
               d.count = 0;
            }
         } else {
            d.count = 0;
         }
      }

      private static final class Data {
         int count;
      }
   }

   public static final class VehicleSpeed extends Check {
      public VehicleSpeed() {
         super("vehicle-speed", "Vehicle Speed", Check.Category.MOVEMENT, "Steering a boat or mount faster than it can go (VehicleBoost, EntityControl, BoatFly speed).");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         Entity v = p.getVehicle();
         boolean boat = v instanceof AbstractBoat;
         if (v == null || !(boat || v instanceof LivingEntity) || v.getControllingPassenger() != p || !survival(p) || InfernoMod.ENGINE.serverLagging()) {
            d.vehicle = null;
            d.count = 0;
         } else {
            double x = v.getX();
            double z = v.getZ();
            if (d.vehicle == v) {
               double h = Math.hypot(x - d.x, z - d.z);
               boolean ice = boat && CheckEngine.blocksNear(p.level(), v.getBoundingBox().expandTowards(0.0, -1.0, 0.0), "ice");
               double limit = InfernoConfig.get().limit(boat ? "vehicle-speed.boat" : "vehicle-speed.mount");
               if (h < 10.0 && !ice && h > limit) {
                  if (++d.count >= 8) {
                     d.count = 0;
                     this.flag(p, (boat ? "boat" : v.getType().toShortString()) + " moving " + round(h) + " b/t (limit " + round(limit) + ")");
                  }
               } else {
                  d.count = 0;
               }
            }

            d.vehicle = v;
            d.x = x;
            d.z = z;
         }
      }

      private static final class Data {
         Entity vehicle;
         double x;
         double z;
         int count;
      }
   }
}
