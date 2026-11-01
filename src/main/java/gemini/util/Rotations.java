package gemini.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class Rotations {
   private Rotations() {
   }

   public static float[] face(Entity from, Entity to) {
      return face(from, to, 0.0);
   }

   public static float[] face(Entity from, Entity to, double ticks) {
      Vec3 motion = to.getDeltaMovement().scale(ticks);
      double ty = to instanceof LivingEntity living ? living.getY() + living.getBbHeight() * 0.85 : to.getY() + to.getBbHeight() * 0.5;
      return look(from.getEyePosition(), new Vec3(to.getX() + motion.x, ty + motion.y, to.getZ() + motion.z));
   }

   public static float[] look(Vec3 eye, Vec3 point) {
      double dx = point.x - eye.x;
      double dy = point.y - eye.y;
      double dz = point.z - eye.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float)(-(Mth.atan2(dy, dist) * (180.0 / Math.PI)));
      return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
   }

   public static float yawDiff(float current, float target) {
      return Math.abs(Mth.wrapDegrees(target - current));
   }
}
