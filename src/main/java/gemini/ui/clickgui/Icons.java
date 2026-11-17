package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.NativeImage;
import gemini.ui.Glass;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The menu icons. The UI font has no icon glyphs, so every icon is a small drawing made of round-capped strokes,
 * rings, discs and filled polygons in a unit square. An icon is rasterised once per size at the screen's real pixel
 * density (antialiased from the exact distance to the shape), kept as a white mask texture and drawn tinted, so it
 * is one quad per icon and sharp at every GUI scale. Where a texture cannot be made a plain dot is drawn instead.
 */
public final class Icons {
   private static final int SEG = 0;
   private static final int RING = 1;
   private static final int DISC = 2;
   private static final int STRIDE = 5;
   private static final Int2ObjectOpenHashMap<Identifier> TEXTURES = new Int2ObjectOpenHashMap<>();
   private static boolean broken;

   private Icons() {
   }

   /** One icon: its strokes, rings and discs ({@link #STRIDE} numbers each) and its filled polygons. */
   public enum Icon {
      COMBAT(new Pen().line(0.16, 0.84, 0.84, 0.16).line(0.2, 0.5, 0.5, 0.8).line(0.66, 0.16, 0.84, 0.16).line(0.84, 0.16, 0.84, 0.34)),
      MOVEMENT(new Pen().path(false, 0.14, 0.2, 0.44, 0.5, 0.14, 0.8).path(false, 0.52, 0.2, 0.82, 0.5, 0.52, 0.8)),
      PLAYER(new Pen().ring(0.5, 0.29, 0.17).arc(0.5, 0.92, 0.34, 180.0, 360.0)),
      WORLD(new Pen().path(true, 0.5, 0.1, 0.86, 0.3, 0.86, 0.7, 0.5, 0.9, 0.14, 0.7, 0.14, 0.3)
         .line(0.5, 0.5, 0.5, 0.9).line(0.5, 0.5, 0.14, 0.3).line(0.5, 0.5, 0.86, 0.3)),
      RENDER(new Pen().curve(0.08, 0.5, 0.5, 0.02, 0.92, 0.5).curve(0.08, 0.5, 0.5, 0.98, 0.92, 0.5).disc(0.5, 0.5, 0.13)),
      HUD(new Pen().path(true, 0.12, 0.2, 0.88, 0.2, 0.88, 0.8, 0.12, 0.8).line(0.28, 0.4, 0.5, 0.4).line(0.6, 0.62, 0.72, 0.62)),
      CLIENT(new Pen().line(0.14, 0.3, 0.86, 0.3).line(0.14, 0.7, 0.86, 0.7).disc(0.62, 0.3, 0.13).disc(0.36, 0.7, 0.13)),
      PROFILES(new Pen().path(true, 0.5, 0.14, 0.88, 0.38, 0.5, 0.62, 0.12, 0.38).path(false, 0.12, 0.62, 0.5, 0.86, 0.88, 0.62)),
      KEYBINDS(new Pen().path(true, 0.1, 0.24, 0.9, 0.24, 0.9, 0.76, 0.1, 0.76)
         .disc(0.3, 0.43, 0.055).disc(0.5, 0.43, 0.055).disc(0.7, 0.43, 0.055).line(0.34, 0.6, 0.66, 0.6)),
      BARITONE(new Pen().ring(0.24, 0.76, 0.1).ring(0.76, 0.24, 0.1).path(false, 0.36, 0.76, 0.62, 0.76, 0.38, 0.24, 0.64, 0.24)),
      MODS(new Pen().line(0.5, 0.12, 0.5, 0.6).path(false, 0.3, 0.42, 0.5, 0.62, 0.7, 0.42).path(false, 0.14, 0.7, 0.14, 0.86, 0.86, 0.86, 0.86, 0.7)),
      SEARCH(new Pen().ring(0.43, 0.43, 0.27).line(0.63, 0.63, 0.86, 0.86)),
      STAR(new Pen().star(true)),
      STAR_OUTLINE(new Pen().star(false)),
      CHECK(new Pen().path(false, 0.18, 0.52, 0.42, 0.75, 0.83, 0.27)),
      CHEVRON_RIGHT(new Pen().path(false, 0.38, 0.22, 0.66, 0.5, 0.38, 0.78)),
      CHEVRON_DOWN(new Pen().path(false, 0.22, 0.38, 0.5, 0.66, 0.78, 0.38)),
      CLOSE(new Pen().line(0.24, 0.24, 0.76, 0.76).line(0.76, 0.24, 0.24, 0.76)),
      LIST(new Pen().disc(0.18, 0.26, 0.06).disc(0.18, 0.5, 0.06).disc(0.18, 0.74, 0.06)
         .line(0.36, 0.26, 0.86, 0.26).line(0.36, 0.5, 0.86, 0.5).line(0.36, 0.74, 0.86, 0.74));

      private final float[] prims;
      private final float[][] polygons;
      private final float halfStroke = 0.05F;

      Icon(Pen pen) {
         this.prims = pen.prims();
         this.polygons = pen.polygons.toArray(new float[0][]);
      }

      /**
       * Signed distance (in units of the icon's side) from the point ({@code x}, {@code y}) in the unit square to the
       * icon's ink: negative inside. Pure.
       */
      public float distance(float x, float y) {
         float best = Float.MAX_VALUE;
         for (int i = 0; i < this.prims.length; i += STRIDE) {
            float a = this.prims[i + 1];
            float b = this.prims[i + 2];
            float c = this.prims[i + 3];
            float d = this.prims[i + 4];
            float dist;
            switch ((int)this.prims[i]) {
               case SEG -> dist = segment(x, y, a, b, c, d) - this.halfStroke;
               case RING -> dist = Math.abs((float)Math.hypot(x - a, y - b) - c) - this.halfStroke;
               default -> dist = (float)Math.hypot(x - a, y - b) - c;
            }

            best = Math.min(best, dist);
         }

         for (float[] polygon : this.polygons) {
            best = Math.min(best, polygon(x, y, polygon));
         }

         return best;
      }

      /** Ink coverage (0..1) of pixel ({@code px}, {@code py}) when the icon is drawn {@code pixels} wide. Pure. */
      public float coverage(int px, int py, int pixels) {
         float distance = this.distance((px + 0.5F) / pixels, (py + 0.5F) / pixels);
         // strokes never get thinner than about a pixel, so small icons stay solid
         float thin = Math.max(0.0F, 0.55F / pixels - this.halfStroke);
         return Math.max(0.0F, Math.min(1.0F, 0.5F - (distance - thin) * pixels));
      }
   }

   /** Collects the primitives of one icon. */
   private static final class Pen {
      private final List<Float> list = new ArrayList<>();
      private final List<float[]> polygons = new ArrayList<>();

      Pen line(double x1, double y1, double x2, double y2) {
         return this.add(SEG, x1, y1, x2, y2);
      }

      Pen ring(double cx, double cy, double r) {
         return this.add(RING, cx, cy, r, 0.0);
      }

      Pen disc(double cx, double cy, double r) {
         return this.add(DISC, cx, cy, r, 0.0);
      }

      /** A polyline through the given x,y pairs; {@code closed} joins the last point to the first. */
      Pen path(boolean closed, double... points) {
         int n = points.length / 2;
         for (int i = 0; i + 1 < n; i++) {
            this.line(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3]);
         }

         if (closed && n > 2) {
            this.line(points[n * 2 - 2], points[n * 2 - 1], points[0], points[1]);
         }

         return this;
      }

      /** Part of a circle from {@code fromDeg} to {@code toDeg} (0 = right, 90 = down), as short strokes. */
      Pen arc(double cx, double cy, double r, double fromDeg, double toDeg) {
         int steps = 14;
         double px = 0.0;
         double py = 0.0;
         for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i > 0) {
               this.line(px, py, x, y);
            }

            px = x;
            py = y;
         }

         return this;
      }

      /** A quadratic curve from (x1, y1) to (x2, y2) bent towards (cx, cy), as short strokes. */
      Pen curve(double x1, double y1, double cx, double cy, double x2, double y2) {
         int steps = 12;
         double px = x1;
         double py = y1;
         for (int i = 1; i <= steps; i++) {
            double t = (double)i / steps;
            double u = 1.0 - t;
            double x = u * u * x1 + 2.0 * u * t * cx + t * t * x2;
            double y = u * u * y1 + 2.0 * u * t * cy + t * t * y2;
            this.line(px, py, x, y);
            px = x;
            py = y;
         }

         return this;
      }

      /** A five-pointed star: a filled polygon, or its outline. */
      Pen star(boolean filled) {
         double[] points = new double[20];
         for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90.0 + i * 36.0);
            double r = i % 2 == 0 ? 0.44 : 0.19;
            points[i * 2] = 0.5 + r * Math.cos(angle);
            points[i * 2 + 1] = 0.53 + r * Math.sin(angle);
         }

         if (filled) {
            float[] polygon = new float[20];
            for (int i = 0; i < 20; i++) {
               polygon[i] = (float)points[i];
            }

            this.polygons.add(polygon);
         }

         // the outline also rounds the points of the filled star
         return this.path(true, points);
      }

      private Pen add(int type, double a, double b, double c, double d) {
         this.list.add((float)type);
         this.list.add((float)a);
         this.list.add((float)b);
         this.list.add((float)c);
         this.list.add((float)d);
         return this;
      }

      float[] prims() {
         float[] out = new float[this.list.size()];
         for (int i = 0; i < out.length; i++) {
            out[i] = this.list.get(i);
         }

         return out;
      }
   }

   /** Distance from a point to a line segment. */
   static float segment(float x, float y, float x1, float y1, float x2, float y2) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = dx * dx + dy * dy;
      float t = length <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, ((x - x1) * dx + (y - y1) * dy) / length));
      return (float)Math.hypot(x - (x1 + dx * t), y - (y1 + dy * t));
   }

   /** Signed distance from a point to a closed polygon given as x,y pairs (negative inside, even-odd rule). */
   static float polygon(float x, float y, float[] points) {
      int n = points.length / 2;
      float best = Float.MAX_VALUE;
      boolean inside = false;
      for (int i = 0, j = n - 1; i < n; j = i++) {
         float xi = points[i * 2];
         float yi = points[i * 2 + 1];
         float xj = points[j * 2];
         float yj = points[j * 2 + 1];
         best = Math.min(best, segment(x, y, xi, yi, xj, yj));
         if (yi > y != yj > y && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside;
         }
      }

      return inside ? -best : best;
   }

   /** Draws {@code icon} in the square at ({@code x}, {@code y}) that is {@code size} GUI units wide, tinted {@code color} (ARGB). */
   public static void draw(GuiGraphicsExtractor g, Icon icon, int x, int y, int size, int color) {
      if (color >>> 24 < 4 || size <= 0) {
         return;
      }

      int pixels = Math.max(4, Math.round(size * Glass.pixelScale(g)));
      Identifier texture = texture(icon, pixels);
      if (texture == null) {
         int inset = Math.max(1, size / 3);
         g.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
         return;
      }

      g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, pixels, pixels, pixels, pixels, color);
   }

   private static Identifier texture(Icon icon, int pixels) {
      if (broken || pixels > 256) {
         return null;
      }

      int key = icon.ordinal() << 12 | pixels;
      Identifier id = TEXTURES.get(key);
      if (id == null) {
         try {
            NativeImage image = new NativeImage(pixels, pixels, false);
            for (int y = 0; y < pixels; y++) {
               for (int x = 0; x < pixels; x++) {
                  image.setPixel(x, y, Math.round(255.0F * icon.coverage(x, y, pixels)) << 24 | 0xFFFFFF);
               }
            }

            id = Identifier.fromNamespaceAndPath("gemini", "icons/" + icon.name().toLowerCase(Locale.ROOT) + "_" + pixels);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Gemini icon", image));
            TEXTURES.put(key, id);
         } catch (RuntimeException | LinkageError e) {
            broken = true;
            return null;
         }
      }

      return id;
   }

   /** The sidebar icon of a category or page, by its enum name. */
   public static Icon named(String name) {
      try {
         return Icon.valueOf(name);
      } catch (IllegalArgumentException e) {
         return Icon.CLIENT;
      }
   }
}
