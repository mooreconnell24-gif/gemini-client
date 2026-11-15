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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * <li>shapes behind the camera are rejected with a dot product, and the rest are tested against the view frustum
 * through a reused scratch box, so an ESP over thousands of blocks only submits what is on screen and a shape
 * that is not submitted allocates nothing at all;</li>
 * <li>lines, labels, points, rings and entity boxes have coordinate overloads, so callers do not build a
 * {@code Vec3} or {@code AABB} per shape per frame just to have it culled;</li>
 * <li>an outlined and filled box is one gizmo, not two;</li>
 * <li>style objects are reused from a small cache instead of being allocated per shape;</li>
 * <li>a frame has a shape budget, so a runaway block list costs a capped amount of frame time instead of
 * freezing the game.</li>
 * </ul>
 *
 * <p>The gizmo collector keeps the box and the end points of what it is given until the frame is uploaded, so a
 * shape that <em>is</em> drawn still costs its one {@code AABB} or two {@code Vec3}: those cannot be pooled without
 * replacing the game's collector. Everything before that point is allocation-free.
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
   /** Boxes up to this many blocks along each axis are frustum-tested through a reused scratch box. */
   private static final int SCRATCH_SPAN = 4;
   private static final BoundingBox[] SCRATCH = new BoundingBox[SCRATCH_SPAN * SCRATCH_SPAN * SCRATCH_SPAN];
   /** Rings are drawn with at most this many segments; the unit circle for them is computed once. */
   private static final int RING_STEPS = 64;
   private static final double[] RING_COS = new double[RING_STEPS + 1];
   private static final double[] RING_SIN = new double[RING_STEPS + 1];

   static {
      for (int i = 0; i <= RING_STEPS; i++) {
         double angle = i * (Math.PI * 2.0) / RING_STEPS;
         RING_COS[i] = Math.cos(angle);
         RING_SIN[i] = Math.sin(angle);
      }
   }

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

      return inFrustum(minX, minY, minZ, maxX, maxY, maxZ);
   }

   /**
    * The frustum test without a new box per call. The game's frustum takes either an {@code AABB} or a whole-block
    * {@code BoundingBox}; the second kind can be moved in place, so one is kept per size (up to
    * {@value #SCRATCH_SPAN} blocks a side) and slid over the shape's enclosing blocks. Rounding outward to whole
    * blocks can only make the test say "visible" for something just off screen, never the reverse. Larger shapes
    * are rare (a chunk column, a selection) and take the {@code AABB} path.
    */
   private static boolean inFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      Frustum view = frustum;
      if (view == null) {
         return true;
      }

      int x0 = Mth.floor(minX);
      int y0 = Mth.floor(minY);
      int z0 = Mth.floor(minZ);
      int spanX = Math.max(0, Mth.ceil(maxX) - 1 - x0);
      int spanY = Math.max(0, Mth.ceil(maxY) - 1 - y0);
      int spanZ = Math.max(0, Mth.ceil(maxZ) - 1 - z0);
      if (spanX >= SCRATCH_SPAN || spanY >= SCRATCH_SPAN || spanZ >= SCRATCH_SPAN) {
         return view.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
      }

      int slot = (spanX * SCRATCH_SPAN + spanY) * SCRATCH_SPAN + spanZ;
      BoundingBox box = SCRATCH[slot];
      if (box == null) {
         box = new BoundingBox(0, 0, 0, spanX, spanY, spanZ);
         SCRATCH[slot] = box;
      }

      box.move(x0 - box.minX(), y0 - box.minY(), z0 - box.minZ());
      int result = view.cubeInFrustum(box);
      // The same two answers Frustum.isVisible(AABB) accepts: fully inside, or crossing a plane.
      return result == -2 || result == -1;
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

      submitBox(
         minX, minY, minZ, maxX, maxY, maxZ, lines ? alpha(color, 255) : 0, lines ? Math.max(1.0F, width) : 0.0F, filled ? alpha(color, fillAlpha) : 0, throughWalls
      );
   }

   /**
    * A box whose outline and faces carry their own alpha, for shapes that fade: {@code strokeArgb} and
    * {@code fillArgb} are full ARGB colours, and either may be 0 to leave that part out.
    */
   public static void boxArgb(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int strokeArgb, int fillArgb, float width, boolean throughWalls
   ) {
      boolean stroke = strokeArgb >>> 24 != 0;
      boolean fill = fillArgb >>> 24 != 0;
      if (!stroke && !fill) {
         return;
      }

      double halfSpan = (maxX - minX + maxY - minY + maxZ - minZ) * 0.5;
      if (!behindCamera((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5, halfSpan + CULL_MARGIN)) {
         submitBox(minX, minY, minZ, maxX, maxY, maxZ, stroke ? strokeArgb : 0, stroke ? Math.max(1.0F, width) : 0.0F, fill ? fillArgb : 0, throughWalls);
      }
   }

   /** Frustum test, budget and submission of a box that is known not to be behind the camera. The box is the only allocation. */
   private static void submitBox(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int strokeArgb, float strokeWidth, int fillArgb, boolean throughWalls
   ) {
      if (!inFrustum(minX, minY, minZ, maxX, maxY, maxZ) || !take()) {
         return;
      }

      GizmoProperties properties = Gizmos.cuboid(
         new AABB(minX - DEPTH_BIAS, minY - DEPTH_BIAS, minZ - DEPTH_BIAS, maxX + DEPTH_BIAS, maxY + DEPTH_BIAS, maxZ + DEPTH_BIAS),
         style(strokeArgb, strokeWidth, fillArgb)
      );
      if (throughWalls) {
         properties.setAlwaysOnTop();
      }
   }

   public static void boxGlow(AABB box, int color, float width, int fillAlpha, boolean throughWalls) {
      if (box != null) {
         boxGlow(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, width, fillAlpha, throughWalls);
      }
   }

   /** An outlined box with a wider, fainter second outline around it, from its corners. */
   public static void boxGlow(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, int fillAlpha, boolean throughWalls
   ) {
      if (visible(minX, minY, minZ, maxX, maxY, maxZ)) {
         box(minX, minY, minZ, maxX, maxY, maxZ, color, true, fillAlpha > 0, width, fillAlpha, throughWalls);
         if (take()) {
            GizmoProperties outer = Gizmos.cuboid(
               new AABB(minX - 0.006, minY - 0.006, minZ - 0.006, maxX + 0.006, maxY + 0.006, maxZ + 0.006),
               style(alpha(color, 95), Math.max(1.0F, width + 1.2F), 0)
            );
            if (throughWalls) {
               outer.setAlwaysOnTop();
            }
         }
      }
   }

   /** Where the entity is drawn this frame, without building a position object. */
   public static double entityX(Entity entity, float partialTick) {
      return Mth.lerp((double)partialTick, entity.xo, entity.getX());
   }

   public static double entityY(Entity entity, float partialTick) {
      return Mth.lerp((double)partialTick, entity.yo, entity.getY());
   }

   public static double entityZ(Entity entity, float partialTick) {
      return Mth.lerp((double)partialTick, entity.zo, entity.getZ());
   }

   /** Whether the entity's box, where it is drawn this frame and grown by {@code grow}, can be on screen. Allocates nothing. */
   public static boolean entityVisible(Entity entity, float partialTick, double grow) {
      AABB box = entity.getBoundingBox();
      double dx = entityX(entity, partialTick) - entity.getX();
      double dy = entityY(entity, partialTick) - entity.getY();
      double dz = entityZ(entity, partialTick) - entity.getZ();
      return visible(box.minX + dx - grow, box.minY + dy - grow, box.minZ + dz - grow, box.maxX + dx + grow, box.maxY + dy + grow, box.maxZ + dz + grow);
   }

   /**
    * The entity's box where it is drawn this frame, grown by {@code grow} on every side. The same shape as
    * {@code box(interpolatedBox(entity, partialTick).inflate(grow), ...)} without the three temporary objects,
    * and without any object at all when the entity is off screen.
    */
   public static void entityBox(
      Entity entity, float partialTick, double grow, int color, boolean lines, boolean fill, float width, int fillAlpha, boolean throughWalls
   ) {
      AABB box = entity.getBoundingBox();
      double dx = entityX(entity, partialTick) - entity.getX();
      double dy = entityY(entity, partialTick) - entity.getY();
      double dz = entityZ(entity, partialTick) - entity.getZ();
      box(
         box.minX + dx - grow, box.minY + dy - grow, box.minZ + dz - grow, box.maxX + dx + grow, box.maxY + dy + grow, box.maxZ + dz + grow,
         color, lines, fill, width, fillAlpha, throughWalls
      );
   }

   /** {@link #entityBox} with the glow outline of {@link #boxGlow}. */
   public static void entityBoxGlow(Entity entity, float partialTick, double grow, int color, float width, int fillAlpha, boolean throughWalls) {
      AABB box = entity.getBoundingBox();
      double dx = entityX(entity, partialTick) - entity.getX();
      double dy = entityY(entity, partialTick) - entity.getY();
      double dz = entityZ(entity, partialTick) - entity.getZ();
      boxGlow(
         box.minX + dx - grow, box.minY + dy - grow, box.minZ + dz - grow, box.maxX + dx + grow, box.maxY + dy + grow, box.maxZ + dz + grow,
         color, width, fillAlpha, throughWalls
      );
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

   /** A line between two points given as coordinates; the end points are only built when the line is submitted. */
   public static void line(double x1, double y1, double z1, double x2, double y2, double z2, int color, float width, boolean throughWalls) {
      lineArgb(x1, y1, z1, x2, y2, z2, alpha(color, 255), width, throughWalls);
   }

   /** {@link #line(double, double, double, double, double, double, int, float, boolean)} with a colour that carries its own alpha. */
   public static void lineArgb(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width, boolean throughWalls) {
      if (!(behindCamera(x1, y1, z1, 0.0) && behindCamera(x2, y2, z2, 0.0)) && take()) {
         GizmoProperties properties = Gizmos.line(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), argb, Math.max(1.0F, width));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   /**
    * A horizontal ring around a point, as {@code segments} straight pieces (clamped to 3..{@value #RING_STEPS}).
    * The circle comes from a table, and a ring that is entirely behind the camera costs one test.
    */
   public static void ring(double x, double y, double z, double radius, int segments, int argb, float width, boolean throughWalls) {
      if (radius <= 0.0 || behindCamera(x, y, z, radius)) {
         return;
      }

      int count = Math.max(3, Math.min(RING_STEPS, segments));
      double previousX = x + radius;
      double previousZ = z;
      for (int i = 1; i <= count; i++) {
         int index = i == count ? 0 : i * RING_STEPS / count;
         double nextX = x + RING_COS[index] * radius;
         double nextZ = z + RING_SIN[index] * radius;
         lineArgb(previousX, y, previousZ, nextX, y, nextZ, argb, width, throughWalls);
         previousX = nextX;
         previousZ = nextZ;
      }
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
            line(x0 + 0.12, y, z0 + 0.12, x0 + 0.88, y, z0 + 0.88, color, width, throughWalls);
            line(x0 + 0.88, y, z0 + 0.12, x0 + 0.12, y, z0 + 0.88, color, width, throughWalls);
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

   /** A label at a point given as coordinates; the position is only built when the label is submitted. */
   public static void text(String text, double x, double y, double z, int color, float scale, boolean throughWalls) {
      if (text != null && !behindCamera(x, y, z, 0.0) && take()) {
         GizmoProperties properties = Gizmos.billboardText(text, new Vec3(x, y, z), Style.forColorAndCentered(alpha(color, 255)).withScale(scale));
         if (throughWalls) {
            properties.setAlwaysOnTop();
         }
      }
   }

   /** A point given as coordinates; the position is only built when the point is submitted. */
   public static void point(double x, double y, double z, int color, float size, boolean throughWalls) {
      if (!behindCamera(x, y, z, 0.0) && take()) {
         GizmoProperties properties = Gizmos.point(new Vec3(x, y, z), alpha(color, 255), size);
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
