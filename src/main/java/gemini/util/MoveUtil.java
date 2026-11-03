package gemini.util;

import gemini.input.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class MoveUtil {
   public static final double WALK_SPEED = 0.2158;
   public static final double SPRINT_SPEED = 0.2806;
   private static final long SETBACK_COOLDOWN_MS = 260L;
   private static volatile long lastSetbackMs;
   private static Vec3 lastClientVelocity = Vec3.ZERO;

   private MoveUtil() {
   }

   public static void onServerSetback(LocalPlayer player) {
      if (player != null) {
         lastSetbackMs = System.currentTimeMillis();
         lastClientVelocity = player.getDeltaMovement().scale(0.45);
      }
   }

   public static boolean hasRecentSetback() {
      return System.currentTimeMillis() - lastSetbackMs < 260L;
   }

   public static double setbackFactor() {
      long elapsed = System.currentTimeMillis() - lastSetbackMs;
      return elapsed >= 260L ? 1.0 : Mth.clamp(0.35 + 0.65 * (elapsed / 260.0), 0.35, 1.0);
   }

   public static void tickInterpolation(LocalPlayer player) {
      if (player != null) {
         if (hasRecentSetback()) {
            Vec3 current = player.getDeltaMovement();
            double factor = setbackFactor();
            Vec3 blended = new Vec3(lerpMotion(lastClientVelocity.x, current.x, factor), current.y, lerpMotion(lastClientVelocity.z, current.z, factor));
            player.setDeltaMovement(blended);
         }

         lastClientVelocity = player.getDeltaMovement();
      }
   }

   public static double lerpMotion(double current, double target, double factor) {
      double clamped = Mth.clamp(factor, 0.0, 1.0);
      return current + (target - current) * clamped;
   }

   public static void smoothVelocity(LocalPlayer player, double factor) {
      Vec3 motion = player.getDeltaMovement();
      player.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
   }

   public static boolean hasInput(LocalPlayer player) {
      if (player.input == null) {
         return false;
      } else {
         Vec2 move = player.input.getMoveVector();
         return move.x != 0.0F || move.y != 0.0F;
      }
   }

   public static double horizontalSpeed(LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      return Math.hypot(motion.x, motion.z);
   }

   public static double baseSpeed(LocalPlayer player) {
      return player.isSprinting() ? 0.2806 : 0.2158;
   }

   public static Vec3 direction(LocalPlayer player) {
      if (player.input == null) {
         return null;
      } else {
         Vec2 move = player.input.getMoveVector();
         double forward = move.y;
         double strafe = move.x;
         double length = Math.hypot(forward, strafe);
         if (length < 1.0E-4) {
            return null;
         } else {
            forward /= length;
            strafe /= length;
            double yaw = Math.toRadians(player.getYRot());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            return new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin);
         }
      }
   }

   public static void setHorizontal(LocalPlayer player, double speed) {
      Vec3 direction = direction(player);
      if (direction != null) {
         double dampedSpeed = speed * setbackFactor();
         Vec3 current = player.getDeltaMovement();
         double targetX = direction.x * dampedSpeed;
         double targetZ = direction.z * dampedSpeed;
         if (hasRecentSetback()) {
            player.setDeltaMovement(lerpMotion(current.x, targetX, 0.5), current.y, lerpMotion(current.z, targetZ, 0.5));
         } else {
            player.setDeltaMovement(targetX, current.y, targetZ);
         }
      }
   }

   public static void teleport(Minecraft client, LocalPlayer player, Vec3 destination, boolean hops) {
      ClientPacketListener connection = client.getConnection();
      if (connection != null && hops) {
         Vec3 position = player.position();
         int count = (int)Math.ceil(position.distanceTo(destination) / 8.0);

         // Hops are marked on-ground: the server adds up every downward hop as fall distance otherwise,
         // so teleporting to somewhere lower would hurt (or kill) on the next step.
         for (int i = 1; i < count; i++) {
            Vec3 point = position.lerp(destination, (double)i / count);
            connection.send(new Pos(point.x, point.y, point.z, true, false));
         }

         connection.send(new Pos(destination.x, destination.y, destination.z, true, false));
      }

      player.setPos(destination);
      player.setDeltaMovement(Vec3.ZERO);
      player.resetFallDistance();
   }

   /**
    * Whether the physical key or mouse button behind a key mapping is held right now, independent of the
    * mapping's (possibly overridden) state. Mouse buttons matter for Use and Attack, which default to the mouse.
    */
   public static boolean physicalKeyDown(KeyMapping mapping) {
      Minecraft client = Minecraft.getInstance();

      try {
         String spec = mapping.saveString();
         Key key = InputConstants.getKey(spec);
         if (key.getType() == Type.KEYBOARD) {
            return InputConstants.isKeyDown(key.getValue());
         } else if (key.getType() == Type.MOUSE) {
            return switch (key.getValue()) {
               case Keys.MOUSE_LEFT -> client.mouseHandler.isLeftPressed();
               case Keys.MOUSE_RIGHT -> client.mouseHandler.isRightPressed();
               case Keys.MOUSE_MIDDLE -> client.mouseHandler.isMiddlePressed();
               default -> mapping.isDown();
            };
         } else {
            return false;
         }
      } catch (RuntimeException ignored) {
         return false;
      }
   }
}
