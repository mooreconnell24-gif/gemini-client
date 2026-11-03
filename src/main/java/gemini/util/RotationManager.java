package gemini.util;

import gemini.event.ActionPriority;
import gemini.module.modules.misc.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class RotationManager {
   public static final int PRIORITY_LOW = 0;
   public static final int PRIORITY_NORMAL = 5;
   public static final int PRIORITY_HIGH = 10;
   private static float targetYaw;
   private static float targetPitch;
   private static int priority = Integer.MIN_VALUE;
   private static boolean active;
   private static boolean silent;
   private static boolean requested;
   private static boolean haveServer;
   private static float serverYaw;
   private static float serverPitch;
   private static boolean replacing;
   private static boolean resetPending;
   private static float yawVelocity;
   private static float pitchVelocity;
   private static float shownYaw;
   private static float shownPitch;
   private static boolean shownValid;

   private RotationManager() {
   }

   public static float[] toward(LocalPlayer player, Vec3 point) {
      Vec3 eye = player.getEyePosition();
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(Mth.atan2(dy, flat) * (180.0 / Math.PI)));
      return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
   }

   public static float[] toward(LocalPlayer player, BlockPos pos) {
      return toward(player, Vec3.atCenterOf(pos));
   }

   public static float[] toward(LocalPlayer player, Entity entity) {
      return Rotations.face(player, entity);
   }

   private static float mouseGcdStep() {
      Minecraft client = Minecraft.getInstance();
      double sens = client.options != null ? client.options.sensitivity().get() : 0.5;
      double f = sens * 0.6 + 0.2;
      return (float)(f * f * f * 1.2);
   }

   /**
    * One tick of movement toward a target angle. With spring smoothing on, the turn accelerates out of rest and
    * settles into the target (a critically damped spring stepped once per tick, capped at {@code maxSpeed}); off, it
    * is the older fixed-rate ease. Either way the step is rounded to the mouse GCD so it looks like real input.
    */
   private static float smoothDelta(float delta, float maxSpeed, boolean yawAxis) {
      float abs = Math.abs(delta);
      if (abs <= 0.001F) {
         if (yawAxis) {
            yawVelocity = 0.0F;
         } else {
            pitchVelocity = 0.0F;
         }

         return 0.0F;
      }

      float step;
      Aim aim = Aim.instance;
      if (aim != null && aim.springTurns()) {
         float stiffness = 0.18F + 0.5F * aim.smoothness();
         float damping = 2.0F * (float)Math.sqrt(stiffness);
         float velocity = yawAxis ? yawVelocity : pitchVelocity;
         velocity += delta * stiffness - velocity * damping;
         velocity = Mth.clamp(velocity, -maxSpeed, maxSpeed);
         // never overshoot: a spring step past the target would wobble on it
         if (Math.abs(velocity) > abs) {
            velocity = Math.copySign(abs, delta);
         }

         if (yawAxis) {
            yawVelocity = velocity;
         } else {
            pitchVelocity = velocity;
         }

         step = velocity;
      } else {
         float eased = abs < maxSpeed ? abs * 0.78F + Math.min(abs * 0.22F, 1.25F) : maxSpeed;
         step = Math.copySign(Math.min(abs, eased), delta);
      }

      float gcd = mouseGcdStep();
      if (gcd > 1.0E-4F) {
         step = Math.round(step / gcd) * gcd;
      }

      return step;
   }

   /** Priority-typed form of {@link #request(LocalPlayer, float, float, float, boolean, int, float)}. */
   public static boolean request(LocalPlayer player, float yaw, float pitch, float speed, boolean quiet, ActionPriority level, float tolerance) {
      return request(player, yaw, pitch, speed, quiet, level.level(), tolerance);
   }

   public static boolean request(LocalPlayer player, float[] rotation, float speed, boolean quiet, ActionPriority level) {
      return request(player, rotation[0], rotation[1], speed, quiet, level.level(), 3.0F);
   }

   /**
    * Asks for the player to face {@code yaw}/{@code pitch}. Within a tick the highest {@code level} wins; equal
    * levels go to whoever asked first (modules run in priority order, so that is the more important module).
    * {@code level} is an {@link ActionPriority#level()}, or one of the old 0/5/8/10 values.
    */
   public static boolean request(LocalPlayer player, float yaw, float pitch, float speed, boolean quiet, int level, float tolerance) {
      if (requested && level < priority) {
         return false;
      } else {
         float fromYaw = quiet && haveServer ? serverYaw : player.getYRot();
         float fromPitch = quiet && haveServer ? serverPitch : player.getXRot();
         float stepYaw;
         float stepPitch;
         if (!(speed <= 0.0F) && !(speed >= 180.0F)) {
            float yawDelta = Mth.wrapDegrees(yaw - fromYaw);
            float pitchDelta = pitch - fromPitch;
            stepYaw = Mth.wrapDegrees(fromYaw + smoothDelta(yawDelta, speed, true));
            stepPitch = Mth.clamp(fromPitch + smoothDelta(pitchDelta, speed * 0.85F, false), -90.0F, 90.0F);
         } else {
            float gcd = mouseGcdStep();
            float yawDelta = Mth.wrapDegrees(yaw - fromYaw);
            float pitchDelta = pitch - fromPitch;
            if (gcd > 1.0E-4F) {
               yawDelta = Math.round(yawDelta / gcd) * gcd;
               pitchDelta = Math.round(pitchDelta / gcd) * gcd;
            }
            stepYaw = Mth.wrapDegrees(fromYaw + yawDelta);
            stepPitch = Mth.clamp(fromPitch + pitchDelta, -90.0F, 90.0F);
         }

         active = true;
         requested = true;
         priority = level;
         silent = quiet;
         targetYaw = stepYaw;
         targetPitch = stepPitch;
         resetPending = false;
         if (!quiet) {
            player.setYRot(stepYaw);
            player.setXRot(stepPitch);
            player.yHeadRot = stepYaw;
         }

         return Rotations.yawDiff(stepYaw, yaw) <= tolerance && Math.abs(stepPitch - pitch) <= tolerance;
      }
   }

   public static boolean request(LocalPlayer player, float[] rotation, float speed, boolean quiet, int level) {
      return request(player, rotation[0], rotation[1], speed, quiet, level, 3.0F);
   }

   public static void beginTick() {
      if (!requested && active) {
         active = false;
         yawVelocity = 0.0F;
         pitchVelocity = 0.0F;
         if (silent) {
            resetPending = true;
         }
      }

      requested = false;
      priority = Integer.MIN_VALUE;
   }

   public static void endTick() {
      if (active && silent) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener connection = client.getConnection();
         if (player != null && connection != null && (!haveServer || serverYaw != targetYaw || serverPitch != targetPitch)) {
            haveServer = true;
            serverYaw = targetYaw;
            serverPitch = targetPitch;
            send(connection, new Rot(targetYaw, targetPitch, player.onGround(), player.horizontalCollision));
         }
      }
   }

   public static float aimYaw() {
      return active ? targetYaw : serverYaw();
   }

   public static float aimPitch() {
      return active ? targetPitch : serverPitch();
   }

   public static float serverYaw() {
      LocalPlayer player = Minecraft.getInstance().player;
      return haveServer ? serverYaw : (player == null ? 0.0F : player.getYRot());
   }

   public static float serverPitch() {
      LocalPlayer player = Minecraft.getInstance().player;
      return haveServer ? serverPitch : (player == null ? 0.0F : player.getXRot());
   }

   public static boolean spoofing() {
      return active && silent;
   }

   /**
    * The silent aim as it should be drawn: eased toward the real server rotation every frame so the marker glides
    * instead of jumping. Returns null when nothing is being aimed silently.
    */
   public static float[] shownAim(float frameSeconds) {
      if (!active || !silent) {
         shownValid = false;
         return null;
      }

      if (!shownValid) {
         shownYaw = targetYaw;
         shownPitch = targetPitch;
         shownValid = true;
      } else {
         float t = Math.min(1.0F, frameSeconds * 22.0F);
         shownYaw = shownYaw + Mth.wrapDegrees(targetYaw - shownYaw) * t;
         shownPitch = shownPitch + (targetPitch - shownPitch) * t;
      }

      return new float[]{shownYaw, shownPitch};
   }

   public static Vec3 serverLook() {
      float yaw = serverYaw();
      float pitch = serverPitch();
      float f = pitch * (float) (Math.PI / 180.0);
      float g = -yaw * (float) (Math.PI / 180.0);
      float h = Mth.cos(g);
      float i = Mth.sin(g);
      float j = Mth.cos(f);
      float k = Mth.sin(f);
      return new Vec3(i * j, -k, h * j);
   }

   /**
    * Runs a module-driven item use (potion, XP bottle, bucket, rod...) while the player briefly faces the current
    * silent aim. Since 1.21 the use-item packet carries the player's own rotation, so without this the throw goes
    * where the camera looks instead of where the module aimed. The camera rotation is restored immediately.
    */
   public static void useWithAim(LocalPlayer player, Runnable action) {
      if (player == null || !active || !silent) {
         action.run();
         return;
      }

      float yaw = player.getYRot();
      float pitch = player.getXRot();
      player.setYRot(targetYaw);
      player.setXRot(targetPitch);

      try {
         action.run();
      } finally {
         player.setYRot(yaw);
         player.setXRot(pitch);
      }
   }

   public static void clear() {
      active = false;
      shownValid = false;
      yawVelocity = 0.0F;
      pitchVelocity = 0.0F;
      requested = false;
      haveServer = false;
      resetPending = false;
      priority = Integer.MIN_VALUE;
   }

   private static void send(ClientPacketListener connection, Packet<?> packet) {
      replacing = true;

      try {
         connection.send(packet);
      } finally {
         replacing = false;
      }
   }

   public static boolean onSendPacket(Packet<?> packet) {
      if (!replacing && packet instanceof ServerboundMovePlayerPacket move) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener connection = client.getConnection();
         if (player == null || connection == null) {
            return false;
         } else if (active && silent) {
            if (!move.hasRotation() && haveServer && serverYaw == targetYaw && serverPitch == targetPitch) {
               return false;
            } else {
               haveServer = true;
               serverYaw = targetYaw;
               serverPitch = targetPitch;
               send(
                  connection,
                  (Packet<?>)(move.hasPosition()
                     ? new PosRot(
                        move.getX(player.getX()),
                        move.getY(player.getY()),
                        move.getZ(player.getZ()),
                        targetYaw,
                        targetPitch,
                        move.isOnGround(),
                        move.horizontalCollision()
                     )
                     : new Rot(targetYaw, targetPitch, move.isOnGround(), move.horizontalCollision()))
               );
               return true;
            }
         } else if (resetPending && !move.hasRotation()) {
            float backYaw = player.getYRot();
            float backPitch = player.getXRot();
            resetPending = false;
            haveServer = true;
            serverYaw = backYaw;
            serverPitch = backPitch;
            send(
               connection,
               (Packet<?>)(move.hasPosition()
                  ? new PosRot(
                     move.getX(player.getX()),
                     move.getY(player.getY()),
                     move.getZ(player.getZ()),
                     backYaw,
                     backPitch,
                     move.isOnGround(),
                     move.horizontalCollision()
                  )
                  : new Rot(backYaw, backPitch, move.isOnGround(), move.horizontalCollision()))
            );
            return true;
         } else {
            if (move.hasRotation()) {
               haveServer = true;
               serverYaw = move.getYRot(player.getYRot());
               serverPitch = move.getXRot(player.getXRot());
               resetPending = false;
            }

            return false;
         }
      } else {
         return false;
      }
   }
}
