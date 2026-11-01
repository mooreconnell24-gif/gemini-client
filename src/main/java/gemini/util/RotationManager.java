package gemini.util;

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

   public static boolean request(LocalPlayer player, float yaw, float pitch, float speed, boolean quiet, int level, float tolerance) {
      if (requested && level < priority) {
         return false;
      } else {
         float fromYaw = quiet && haveServer ? serverYaw : player.getYRot();
         float fromPitch = quiet && haveServer ? serverPitch : player.getXRot();
         float stepYaw;
         float stepPitch;
         if (!(speed <= 0.0F) && !(speed >= 180.0F)) {
            stepYaw = Mth.wrapDegrees(fromYaw + Mth.clamp(Mth.wrapDegrees(yaw - fromYaw), -speed, speed));
            stepPitch = Mth.clamp(fromPitch + Mth.clamp(pitch - fromPitch, -speed, speed), -90.0F, 90.0F);
         } else {
            stepYaw = Mth.wrapDegrees(yaw);
            stepPitch = Mth.clamp(pitch, -90.0F, 90.0F);
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

   public static void clear() {
      active = false;
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
