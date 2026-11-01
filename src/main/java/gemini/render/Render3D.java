package gemini.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo.Style;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class Render3D {
   private static final double DEPTH_BIAS = 0.002;
   private static Frustum frustum;

   private Render3D() {
   }

   public static void beginFrame(Frustum viewFrustum) {
      frustum = viewFrustum;
   }

   public static void endFrame() {
      frustum = null;
   }

   public static boolean visible(AABB box) {
      if (box == null) {
         return false;
      } else {
         Frustum view = frustum;
         return view == null || view.isVisible(box.inflate(0.05));
      }
   }

   public static boolean visible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return visible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
   }

   public static boolean visibleBlock(int x, int y, int z) {
      return visible(x, y, z, x + 1.0, y + 1.0, z + 1.0);
   }

   public static int alpha(int color, int alpha) {
      return ARGB.color(Math.max(0, Math.min(255, alpha)), color & 16777215);
   }

   public static void box(AABB box, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      if (box != null && (lines || fill) && visible(box)) {
         AABB biased = box.inflate(0.002);
         float strokeWidth = lines ? Math.max(1.0F, width) : 0.0F;
         if (fill && fillAlpha > 0) {
            GizmoProperties fillProps = Gizmos.cuboid(biased, new GizmoStyle(0, 0.0F, alpha(color, fillAlpha)));
            if (throughWalls) {
               fillProps.setAlwaysOnTop();
            }
         }

         if (lines) {
            GizmoProperties lineProps = Gizmos.cuboid(biased, new GizmoStyle(alpha(color, 255), strokeWidth, 0));
            if (throughWalls) {
               lineProps.setAlwaysOnTop();
            }
         }
      }
   }

   public static void boxGlow(AABB box, int color, float width, int fillAlpha, boolean throughWalls) {
      if (box != null && visible(box)) {
         box(box, color, true, fillAlpha > 0, width, fillAlpha, throughWalls);
         GizmoProperties outer = Gizmos.cuboid(box.inflate(0.006), new GizmoStyle(alpha(color, 95), Math.max(1.0F, width + 1.2F), 0));
         if (throughWalls) {
            outer.setAlwaysOnTop();
         }
      }
   }

   public static void block(BlockPos pos, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      if (pos != null) {
         box(new AABB(pos), color, lines, fill, width, fillAlpha, throughWalls);
      }
   }

   public static void line(Vec3 from, Vec3 to, int color, float width, boolean throughWalls) {
      if (from != null && to != null) {
         GizmoProperties properties = Gizmos.line(from, to, alpha(color, 255), Math.max(1.0F, width));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   public static void lineArgb(Vec3 from, Vec3 to, int argb, float width, boolean throughWalls) {
      if (from != null && to != null) {
         GizmoProperties properties = Gizmos.line(from, to, argb, Math.max(1.0F, width));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   public static void floorMark(BlockPos pos, double lift, int color, boolean cross, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      double y = pos.getY() + lift + 0.002;
      double x0 = pos.getX();
      double z0 = pos.getZ();
      if (visible(x0, y - 0.05, z0, x0 + 1.0, y + 0.05, z0 + 1.0)) {
         if (fill) {
            GizmoProperties quad = Gizmos.rect(
               new Vec3(x0 + 0.05, y, z0 + 0.05), new Vec3(x0 + 0.95, y, z0 + 0.95), Direction.UP, new GizmoStyle(0, 0.0F, alpha(color, fillAlpha))
            );
            if (throughWalls) {
               quad.setAlwaysOnTop();
            }
         }

         if (cross) {
            line(new Vec3(x0 + 0.12, y, z0 + 0.12), new Vec3(x0 + 0.88, y, z0 + 0.88), color, width, throughWalls);
            line(new Vec3(x0 + 0.88, y, z0 + 0.12), new Vec3(x0 + 0.12, y, z0 + 0.88), color, width, throughWalls);
         }
      }
   }

   public static void text(String text, Vec3 position, int color, float scale, boolean throughWalls) {
      if (text != null && position != null) {
         GizmoProperties properties = Gizmos.billboardText(text, position, Style.forColorAndCentered(alpha(color, 255)).withScale(scale));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   public static void point(Vec3 position, int color, float size, boolean throughWalls) {
      if (position != null) {
         GizmoProperties properties = Gizmos.point(position, alpha(color, 255), size);
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   public static AABB interpolatedBox(Entity entity, float partialTick) {
      Vec3 drawn = entity.getPosition(partialTick);
      return entity.getBoundingBox().move(drawn.x - entity.getX(), drawn.y - entity.getY(), drawn.z - entity.getZ());
   }

   public static Camera camera() {
      return Minecraft.getInstance().gameRenderer.mainCamera();
   }

   public static Vec3 crosshair() {
      Camera camera = camera();
      Vector3fc forward = camera.forwardVector();
      Vec3 position = camera.position();
      return new Vec3(position.x + forward.x() * 0.4, position.y + forward.y() * 0.4, position.z + forward.z() * 0.4);
   }

   public static float partialTick() {
      return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
   }
}
