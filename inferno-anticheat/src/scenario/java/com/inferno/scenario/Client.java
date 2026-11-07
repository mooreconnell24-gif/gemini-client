package com.inferno.scenario;

import java.util.function.DoubleBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the client side for a fake player: sends packets in the order a vanilla 26.3 client does
 * (input, sprint command, movement, tick-end) and, for legit scenarios, moves with vanilla's walking
 * physics (LivingEntity#travel) on simple terrain.
 */
final class Client {
   final ServerPlayer p;
   final ServerLevel level;
   final ServerGamePacketListenerImpl net;
   double x;
   double y;
   double z;
   double vx;
   double vy;
   double vz;
   float yaw;
   float pitch;
   boolean onGround = true;
   boolean sprinting;
   int jumpDelay;
   Input input = Input.EMPTY;
   /** Top of the ground under a player standing at (x, z), counting the whole 0.6-wide footprint. */
   DoubleBinaryOperator ground;
   /** SafeWalk emulation: never let z pass this. */
   double maxZ = Double.MAX_VALUE;
   int sequence;
   /** Scenario sends its own tick-end packets. */
   boolean manualTickEnd;
   // Cheat switches for the physics model.
   /** Jump without reporting the jump key (Speed/BHop auto-jump). */
   boolean hideJumpKey;
   /** Jump impulse (HighJump changes it). */
   double jumpPower = 0.42;
   /** ReverseStep: snap straight down to the ground below when walking off a ledge. */
   boolean snapDown;

   Client(ServerPlayer p, double x, double y, double z) {
      this.p = p;
      this.level = p.level();
      this.net = p.connection;
      this.x = x;
      this.y = y;
      this.z = z;
      this.ground = (px, pz) -> y;
   }

   // ---- raw packets ----

   void input(Input in) {
      if (!in.equals(this.input)) {
         this.net.handlePlayerInput(new ServerboundPlayerInputPacket(in));
         this.input = in;
      }
   }

   void keys(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean shift, boolean sprintKey) {
      this.input(new Input(w, s, a, d, jump, shift, sprintKey));
   }

   void sprint(boolean on) {
      if (on != this.sprinting) {
         this.command(on ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
         this.sprinting = on;
      }
   }

   void command(ServerboundPlayerCommandPacket.Action action) {
      this.net.handlePlayerCommand(new ServerboundPlayerCommandPacket(this.p, action));
   }

   void move(double x, double y, double z, boolean onGround) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.onGround = onGround;
      this.net.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(x, y, z, this.yaw, this.pitch, onGround, false));
   }

   void rot(float yaw, float pitch) {
      this.yaw = yaw;
      this.pitch = pitch;
      this.net.handleMovePlayer(new ServerboundMovePlayerPacket.Rot(yaw, pitch, this.onGround, false));
   }

   void tickEnd() {
      this.net.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
   }

   void slot(int slot) {
      this.net.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
   }

   void action(ServerboundPlayerActionPacket.Action action, BlockPos pos) {
      this.net.handlePlayerAction(new ServerboundPlayerActionPacket(action, pos, Direction.UP, ++this.sequence));
   }

   void swing() {
      this.net.handlePunch(ServerboundPunchPacket.INSTANCE);
   }

   void useItem() {
      this.net.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, ++this.sequence, this.yaw, this.pitch));
   }

   /** Right-click the top face of {@code pos}. */
   void useOn(BlockPos pos) {
      this.useOn(pos, Direction.UP);
   }

   void useOn(BlockPos pos, Direction face) {
      Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
      this.net.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false), ++this.sequence));
   }

   void attack(Entity e) {
      this.net.handleAttack(new ServerboundAttackPacket(e.getId()));
   }

   /** Rotation that points the eyes at a point, like a player turning the mouse onto it. */
   void lookAt(Vec3 target) {
      Vec3 eye = new Vec3(this.x, this.y + 1.62, this.z);
      Vec3 d = target.subtract(eye);
      float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
      float pitch = (float)Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
      this.rot(yaw, pitch);
   }

   // ---- vanilla physics ----

   /**
    * One vanilla client tick on the ground model: keys -> sprint state -> jump -> travel -> packets.
    * Mirrors LocalPlayer#aiStep / LivingEntity#travelInAir for normal blocks (friction 0.6).
    */
   void walk(boolean w, boolean a, boolean s, boolean d, boolean jump, boolean sprintKey, boolean sneak, float yawNow) {
      this.keys(w, a, s, d, jump && !this.hideJumpKey, sneak, sprintKey);
      boolean forwardImpulse = w && !s;
      boolean wantSprint = this.sprinting ? forwardImpulse : forwardImpulse && sprintKey && !sneak;
      double fwd = (w ? 1 : 0) - (s ? 1 : 0);
      double str = (a ? 1 : 0) - (d ? 1 : 0);
      if (sneak) {
         fwd *= 0.3;
         str *= 0.3;
      }

      fwd *= 0.98;
      str *= 0.98;
      boolean startGround = this.onGround;
      if (this.jumpDelay > 0) {
         this.jumpDelay--;
      }

      if (jump) {
         if (this.onGround && this.jumpDelay == 0) {
            this.vy = this.jumpPower;
            if (wantSprint) {
               double r = Math.toRadians(yawNow);
               this.vx += -Math.sin(r) * 0.2;
               this.vz += Math.cos(r) * 0.2;
            }

            this.jumpDelay = 10;
         }
      } else {
         this.jumpDelay = 0;
      }

      double speed = startGround ? 0.1 * (wantSprint ? 1.3 : 1.0) : (wantSprint ? 0.026 : 0.02);
      double len = fwd * fwd + str * str;
      if (len > 1.0E-7) {
         if (len > 1.0) {
            double n = Math.sqrt(len);
            fwd /= n;
            str /= n;
         }

         double r = Math.toRadians(yawNow);
         double sin = Math.sin(r);
         double cos = Math.cos(r);
         double ix = str * speed;
         double iz = fwd * speed;
         this.vx += ix * cos - iz * sin;
         this.vz += iz * cos + ix * sin;
      }

      double nx = this.x + this.vx;
      double nz = this.z + this.vz;
      if (nz > this.maxZ) {
         nz = this.maxZ;
         this.vz = 0.0;
      }

      double ny = this.y + this.vy;
      double g = this.ground.applyAsDouble(nx, nz);
      boolean landed = ny <= g;
      if (this.snapDown && startGround && !landed && this.vy <= 0.0) {
         landed = true;
      }
      if (landed) {
         ny = g;
      }

      double friction = startGround ? 0.6 * 0.91 : 0.91;
      this.vx *= friction;
      this.vz *= friction;
      if (landed) {
         this.vy = 0.0;
      }

      this.vy = (this.vy - 0.08) * 0.98;
      boolean moved = nx != this.x || ny != this.y || nz != this.z;
      boolean rotated = yawNow != this.yaw;
      this.yaw = yawNow;
      this.sprint(wantSprint);
      if (moved || rotated) {
         this.move(nx, ny, nz, landed);
      } else {
         this.onGround = landed;
      }
   }
}
