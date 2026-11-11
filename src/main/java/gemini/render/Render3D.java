package gemini.render;

import java.util.List;
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

/**
 * Every 3D shape Gemini draws goes through here and from here into Minecraft's gizmo collector, which gathers the
 * frame's primitives and uploads them in batches through the game's own buffer builders. Nothing in the client
 * draws in immediate mode or owns a vertex buffer.
 *
 * <p>What this class adds on top is keeping work away from that collector:
 * <ul>
 * <li>shapes behind the camera are rejected with a dot product before anything is allocated, and the rest are
 * tested against the view frustum, so an ESP over thousands of blocks only submits what is on screen;</li>
 * <li>an outlined and filled box is one gizmo, not two;</li>
 * <li>style objects are reused from a small cache instead of being allocated per shape;</li>
 * <li>a frame has a shape budget, so a runaway block list costs a capped amount of frame time instead of
 * freezing the game.</li>
 * </ul>
 *
 * <p>Only call the drawing methods from a {@code onRender3D} hook; the collector exists only then.
 */
public final class Render3D {
   /** Boxes are grown by this much so their faces do not z-fight with the block they wrap. */
   private static final double DEPTH_BIAS = 0.002;
   private static final double CULL_MARGIN = 0.05;
   /** Shapes submitted per frame before the rest are dropped. Each box is up to 6 quads and 12 lines. */
   public static final int MAX_SHAPES_PER_FRAME = 24000;
   private static final int STYLE_SLOTS = 64;
   private static final GizmoStyle[] STYLES = new GizmoStyle[STYLE_SLOTS];
   private static Frustum frustum;
   private static boolean hasCamera;
   private static double camX;
   private static double camY;
   private static double camZ;
   private static double forwardX;
   private static double forwardY;
   private static double forwardZ;
   private static int shapes;
   private static int dropped;
   private static int lastFrameShapes;
   private static int lastFrameDropped;

   private Render3D() {
   }

   public static void beginFrame(Frustum viewFrustum) {
      frustum = viewFrustum;
      shapes = 0;
      dropped = 0;
      Camera camera = camera();
      if (camera != null) {
         Vec3 position = camera.position();
         Vector3fc forward = camera.forwardVector();
         camX = position.x;
         camY = position.y;
         camZ = position.z;
         forwardX = forward.x();
         forwardY = forward.y();
         forwardZ = forward.z();
         hasCamera = true;
      } else {
         hasCamera = false;
      }
   }

   public static void endFrame() {
      frustum = null;
      hasCamera = false;
      lastFrameShapes = shapes;
      lastFrameDropped = dropped;
   }

   /** Shapes submitted in the last finished frame. */
   public static int lastFrameShapes() {
      return lastFrameShapes;
   }

   /** Shapes left out of the last finished frame because the budget was spent. */
   public static int lastFrameDropped() {
      return lastFrameDropped;
   }

   /** Counts a shape against the frame budget; false when the budget is spent and the shape must be skipped. */
   private static boolean take() {
      if (shapes >= MAX_SHAPES_PER_FRAME) {
         dropped++;
         return false;
      }

      shapes++;
      return true;
   }

   /** True when a sphere around the point lies entirely behind the camera. Costs a dot product and allocates nothing. */
   public static boolean behindCamera(double x, double y, double z, double radius) {
      return hasCamera && (x - camX) * forwardX + (y - camY) * forwardY + (z - camZ) * forwardZ < -radius;
   }

   public static boolean visible(AABB box) {
      if (box == null) {
         return false;
      }

      return visible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
   }

   public static boolean visible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double halfSpan = (maxX - minX + maxY - minY + maxZ - minZ) * 0.5;
      if (behindCamera((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5, halfSpan + CULL_MARGIN)) {
         return false;
      }

      Frustum view = frustum;
      return view == null
         || view.isVisible(new AABB(minX - CULL_MARGIN, minY - CULL_MARGIN, minZ - CULL_MARGIN, maxX + CULL_MARGIN, maxY + CULL_MARGIN, maxZ + CULL_MARGIN));
   }

   public static boolean visibleBlock(int x, int y, int z) {
      return visible(x, y, z, x + 1.0, y + 1.0, z + 1.0);
   }

   public static int alpha(int color, int alpha) {
      return ARGB.color(Math.max(0, Math.min(255, alpha)), color & 16777215);
   }

   /** A style from the cache; styles are immutable, and a frame only ever uses a handful of distinct ones. */
   private static GizmoStyle style(int stroke, float strokeWidth, int fill) {
      int slot = (stroke * 31 + fill * 17 + Float.floatToIntBits(strokeWidth)) & STYLE_SLOTS - 1;
      GizmoStyle cached = STYLES[slot];
      if (cached != null && cached.stroke() == stroke && cached.fill() == fill && cached.strokeWidth() == strokeWidth) {
         return cached;
      }

      GizmoStyle created = new GizmoStyle(stroke, strokeWidth, fill);
      STYLES[slot] = created;
      return created;
   }

   public static void box(AABB box, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      if (box != null) {
         box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, lines, fill, width, fillAlpha, throughWalls);
      }
   }

   /** A box from its corners: outline, filled faces or both, as one gizmo. */
   public static void box(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls
   ) {
      boolean filled = fill && fillAlpha > 0;
      if (!lines && !filled) {
         return;
      }

      double halfSpan = (maxX - minX + maxY - minY + maxZ - minZ) * 0.5;
      if (behindCamera((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5, halfSpan + CULL_MARGIN)) {
         return;
      }

      // One box serves both the frustum test and the gizmo.
      AABB biased = new AABB(minX - DEPTH_BIAS, minY - DEPTH_BIAS, minZ - DEPTH_BIAS, maxX + DEPTH_BIAS, maxY + DEPTH_BIAS, maxZ + DEPTH_BIAS);
      Frustum view = frustum;
      if (view != null && !view.isVisible(biased) || !take()) {
         return;
      }

      GizmoProperties properties = Gizmos.cuboid(
         biased, style(lines ? alpha(color, 255) : 0, lines ? Math.max(1.0F, width) : 0.0F, filled ? alpha(color, fillAlpha) : 0)
      );
      if (throughWalls) {
         properties.setAlwaysOnTop();
      }
   }

   public static void boxGlow(AABB box, int color, float width, int fillAlpha, boolean throughWalls) {
      if (box != null && visible(box)) {
         // The inner box repeats the cheap behind-camera test only; the frustum has already said yes.
         box(box, color, true, fillAlpha > 0, width, fillAlpha, throughWalls);
         if (take()) {
            GizmoProperties outer = Gizmos.cuboid(box.inflate(0.006), style(alpha(color, 95), Math.max(1.0F, width + 1.2F), 0));
            if (throughWalls) {
               outer.setAlwaysOnTop();
            }
         }
      }
   }

   public static void block(BlockPos pos, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      if (pos != null) {
         block(pos.getX(), pos.getY(), pos.getZ(), color, lines, fill, width, fillAlpha, throughWalls);
      }
   }

   /** A full block at integer coordinates, without building a position or box object first. */
   public static void block(int x, int y, int z, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls) {
      box(x, y, z, x + 1.0, y + 1.0, z + 1.0, color, lines, fill, width, fillAlpha, throughWalls);
   }

   /** True when both ends of a segment are behind the camera, so the segment cannot be on screen. */
   private static boolean segmentBehind(Vec3 from, Vec3 to) {
      return behindCamera(from.x, from.y, from.z, 0.0) && behindCamera(to.x, to.y, to.z, 0.0);
   }

   /** Connected line segments through the given points, skipping segments that are entirely behind the camera. */
   public static void polyline(List<Vec3> points, int color, float width, boolean throughWalls) {
      if (points == null || points.size() < 2) {
         return;
      }

      int argb = alpha(color, 255);
      Vec3 previous = points.get(0);
      for (int i = 1; i < points.size(); i++) {
         Vec3 next = points.get(i);
         lineArgb(previous, next, argb, width, throughWalls);
         previous = next;
      }
   }

   public static void line(Vec3 from, Vec3 to, int color, float width, boolean throughWalls) {
      lineArgb(from, to, alpha(color, 255), width, throughWalls);
   }

   public static void lineArgb(Vec3 from, Vec3 to, int argb, float width, boolean throughWalls) {
      if (from != null && to != null && !segmentBehind(from, to) && take()) {
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
         if (fill && take()) {
            GizmoProperties quad = Gizmos.rect(
               new Vec3(x0 + 0.05, y, z0 + 0.05), new Vec3(x0 + 0.95, y, z0 + 0.95), Direction.UP, style(0, 0.0F, alpha(color, fillAlpha))
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
      if (text != null && position != null && !behindCamera(position.x, position.y, position.z, 0.0) && take()) {
         GizmoProperties properties = Gizmos.billboardText(text, position, Style.forColorAndCentered(alpha(color, 255)).withScale(scale));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   public static void point(Vec3 position, int color, float size, boolean throughWalls) {
      if (position != null && !behindCamera(position.x, position.y, position.z, 0.0) && take()) {
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
