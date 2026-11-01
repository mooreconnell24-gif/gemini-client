package gemini.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Rotations {
   private Rotations() {
   }

   public static float[] face(Entity from, Entity to) {
      return face(from, to, 0.0);
   }

   public static float[] face(Entity from, Entity to, double ticks) {
      Vec3 eye = from.getEyePosition();
      Vec3 motion = to.getDeltaMovement().scale(ticks);
      AABB box = to.getBoundingBox().move(motion);
      double marginX = Math.min(0.12, (box.maxX - box.minX) * 0.25);
      double marginY = Math.min(0.14, (box.maxY - box.minY) * 0.2);
      double marginZ = Math.min(0.12, (box.maxZ - box.minZ) * 0.25);
      double nearX = Mth.clamp(eye.x, box.minX + marginX, box.maxX - marginX);
      double nearY = Mth.clamp(eye.y, box.minY + marginY, box.maxY - marginY);
      double nearZ = Mth.clamp(eye.z, box.minZ + marginZ, box.maxZ - marginZ);
      double centerX = (box.minX + box.maxX) * 0.5;
      double centerY = box.minY + (box.maxY - box.minY) * 0.78;
      double centerZ = (box.minZ + box.maxZ) * 0.5;
      double aimX = Mth.lerp(0.35, nearX, centerX);
      double aimY = Mth.lerp(0.45, nearY, centerY);
      double aimZ = Mth.lerp(0.35, nearZ, centerZ);
      return look(eye, new Vec3(aimX, aimY, aimZ));
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
