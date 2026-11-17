package gemini.ui;

import java.util.Random;

/**
 * The drifting network behind {@link GeminiTitleScreen}: points in three depth layers, each joined to its near
 * neighbours in the same layer by thin lines. This class holds only the state and the arithmetic (no Minecraft
 * types), so it can be tested; the screen draws it.
 *
 * <p>Depth is what makes it calm rather than busy. Far points are smaller, dimmer, slower and move less with the
 * mouse; near points are larger and brighter and shift the most. Every layer only links to itself, so the three
 * nets slide over one another like layers of haze.
 *
 * <p>Positions are kept in a domain a little larger than the screen ({@link #margin}), and a point wraps around
 * that larger domain: by the time it jumps it is far enough outside the screen that neither the point nor any of
 * its links can be seen, so nothing pops. Link opacity falls off smoothly to zero at the link reach (with zero
 * slope), so links fade in and out instead of switching.
 *
 * <p>All arrays are allocated once, for {@link #MAX_POINTS}; {@link #update} and the accessors allocate nothing.
 * Coordinates are in physical window pixels.
 */
public final class NetworkField {
   public static final int LAYERS = 3;
   public static final int MAX_POINTS = 320;
   /**
    * Area (physical pixels) of the domain per point. The domain includes the margin, so this keeps the density on
    * screen the same at every size: about 290 points (130 of them on screen) at 1280x720, 130 at 854x480.
    */
   static final float AREA_PER_POINT = 7_000.0F;
   /** Share of the points in each layer, far to near. */
   static final float[] LAYER_SHARE = {0.50F, 0.33F, 0.17F};
   /** Depth of each layer (0 = infinitely far, 1 = nearest); scales speed, parallax, size and brightness. */
   static final float[] LAYER_DEPTH = {0.30F, 0.60F, 1.00F};
   /** Drift speed of a depth-1 point, in fractions of the short screen side per second. */
   static final float SPEED = 0.010F;
   /** Parallax shift of a depth-1 point at the screen edge, in fractions of the short screen side. */
   static final float PARALLAX = 0.030F;
   /** Link reach of a depth-1 layer, in fractions of the short screen side. */
   static final float REACH = 0.28F;

   private final float[] u = new float[MAX_POINTS];
   private final float[] v = new float[MAX_POINTS];
   private final float[] du = new float[MAX_POINTS];
   private final float[] dv = new float[MAX_POINTS];
   private final float[] x = new float[MAX_POINTS];
   private final float[] y = new float[MAX_POINTS];
   private final int[] layer = new int[MAX_POINTS];
   /** Start index of each layer in the arrays (points are stored layer by layer), plus the end. */
   private final int[] layerStart = new int[LAYERS + 1];
   private int count;
   private float width = 1.0F;
   private float height = 1.0F;

   public NetworkField(long seed) {
      Random random = new Random(seed);
      for (int i = 0; i < MAX_POINTS; i++) {
         this.u[i] = random.nextFloat();
         this.v[i] = random.nextFloat();
         double angle = random.nextDouble() * Math.PI * 2.0;
         // Speeds vary a little within a layer so the net slowly changes shape instead of sliding as one sheet.
         float speed = 0.6F + random.nextFloat() * 0.8F;
         this.du[i] = (float)Math.cos(angle) * speed;
         this.dv[i] = (float)Math.sin(angle) * speed;
      }

      this.resize(1280, 720);
   }

   // ---- pure maths ------------------------------------------------------------------------------------------

   /**
    * How many points a window of this physical size gets: proportional to the area of its domain (the window plus
    * the {@link #margin}), at least 12, at most {@link #MAX_POINTS}.
    */
   public static int pointsFor(int width, int height) {
      float w = Math.max(1, width);
      float h = Math.max(1, height);
      float m = margin(w, h);
      float area = (w + 2.0F * m) * (h + 2.0F * m);
      return Math.max(12, Math.min(MAX_POINTS, Math.round(area / AREA_PER_POINT)));
   }

   /**
    * Start index of each layer when {@code count} points are split by {@link #LAYER_SHARE}, far layer first, plus
    * {@code count} as the last entry. Each layer gets at least one point when there are enough points to go round.
    */
   public static int[] layerStarts(int count, int[] out) {
      out[0] = 0;
      int start = 0;
      float share = 0.0F;
      for (int l = 0; l < LAYERS - 1; l++) {
         share += LAYER_SHARE[l];
         int end = Math.round(count * share);
         int minEnd = Math.min(count, start + 1);
         int maxEnd = Math.max(minEnd, count - (LAYERS - 1 - l));
         start = Math.max(minEnd, Math.min(maxEnd, end));
         out[l + 1] = start;
      }

      out[LAYERS] = count;
      return out;
   }

   /** {@code value} wrapped into [0, 1). */
   public static float wrap(float value) {
      float wrapped = value - (float)Math.floor(value);
      return wrapped >= 1.0F ? 0.0F : wrapped;
   }

   /**
    * How far outside the screen the domain reaches, in pixels: past the longest link and the largest parallax
    * shift plus a point's size, so a point that wraps is never on screen and never linked to anything on screen.
    */
   public static float margin(float width, float height) {
      float unit = Math.min(width, height);
      return unit * (REACH * LAYER_DEPTH[LAYERS - 1] + PARALLAX * LAYER_DEPTH[LAYERS - 1]) + 8.0F;
   }

   /** Screen position of a domain coordinate {@code t} (0..1) along a side of {@code size} pixels with {@code margin} on each end. */
   public static float toScreen(float t, float size, float margin) {
      return -margin + t * (size + 2.0F * margin);
   }

   /**
    * Opacity factor (0..1) of a link between two points {@code distance} apart when the reach is {@code reach}:
    * 1 when they touch, falling smoothly to exactly 0 at the reach with no kink, so links fade rather than pop.
    */
   public static float linkAlpha(float distance, float reach) {
      if (reach <= 0.0F || distance >= reach) {
         return 0.0F;
      }

      float t = Math.max(0.0F, distance) / reach;
      float k = 1.0F - t * t;
      return k * k;
   }

   /** Parallax shift in pixels of a point at {@code depth} for an eased mouse offset {@code offset} (-1..1). */
   public static float parallax(float offset, float depth, float unit) {
      return -offset * depth * PARALLAX * unit;
   }

   /**
    * Opacity factor for a point at ({@code px}, {@code py}) near a calm zone (the rectangle {@code x1..x2},
    * {@code y1..y2}): {@code floor} inside it, rising smoothly to 1 at {@code soft} pixels outside it.
    */
   public static float calm(float px, float py, float x1, float y1, float x2, float y2, float soft, float floor) {
      float dx = Math.max(0.0F, Math.max(x1 - px, px - x2));
      float dy = Math.max(0.0F, Math.max(y1 - py, py - y2));
      float distance = (float)Math.sqrt(dx * dx + dy * dy);
      if (soft <= 0.0F) {
         return distance > 0.0F ? 1.0F : floor;
      }

      float t = Math.min(1.0F, distance / soft);
      float s = t * t * (3.0F - 2.0F * t);
      return floor + (1.0F - floor) * s;
   }

   /** Depth of layer {@code l}. */
   public static float depth(int l) {
      return LAYER_DEPTH[Math.max(0, Math.min(LAYERS - 1, l))];
   }

   // ---- state -----------------------------------------------------------------------------------------------

   /** Sets the window size (physical pixels); the number of points follows the area. Positions are kept. */
   public void resize(int width, int height) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.count = pointsFor(width, height);
      layerStarts(this.count, this.layerStart);
      for (int l = 0; l < LAYERS; l++) {
         for (int i = this.layerStart[l]; i < this.layerStart[l + 1]; i++) {
            this.layer[i] = l;
         }
      }
   }

   /**
    * Moves every point by {@code dt} seconds of drift (0 to freeze it) and places it on screen for the eased mouse
    * offset {@code parallaxX}, {@code parallaxY} (each -1..1).
    */
   public void update(float dt, float parallaxX, float parallaxY) {
      float unit = Math.min(this.width, this.height);
      float margin = margin(this.width, this.height);
      float spanX = this.width + 2.0F * margin;
      float spanY = this.height + 2.0F * margin;
      for (int i = 0; i < this.count; i++) {
         float depth = LAYER_DEPTH[this.layer[i]];
         if (dt > 0.0F) {
            // Speed in pixels, converted to the domain of each axis so the drift is the same in both directions.
            float step = SPEED * depth * unit * dt;
            this.u[i] = wrap(this.u[i] + this.du[i] * step / spanX);
            this.v[i] = wrap(this.v[i] + this.dv[i] * step / spanY);
         }

         this.x[i] = toScreen(this.u[i], this.width, margin) + parallax(parallaxX, depth, unit);
         this.y[i] = toScreen(this.v[i], this.height, margin) + parallax(parallaxY, depth, unit) * 0.6F;
      }
   }

   public int count() {
      return this.count;
   }

   public int layerStart(int l) {
      return this.layerStart[l];
   }

   public int layerEnd(int l) {
      return this.layerStart[l + 1];
   }

   public int layer(int i) {
      return this.layer[i];
   }

   public float x(int i) {
      return this.x[i];
   }

   public float y(int i) {
      return this.y[i];
   }

   /** Link reach of layer {@code l} in pixels: nearer layers reach further, as their spacing looks larger. */
   public float reach(int l) {
      return REACH * (0.55F + 0.45F * depth(l)) * Math.min(this.width, this.height);
   }

   public float width() {
      return this.width;
   }

   public float height() {
      return this.height;
   }
}
