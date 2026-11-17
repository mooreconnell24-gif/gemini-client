package gemini.ui;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Small antialiased shapes for the HUD that the UI font has no glyphs for: the notification icons (check, cross,
 * info, warning) and a circular progress ring.
 *
 * <p>Each icon is a white mask baked once per size in real pixels from a few strokes and dots (see
 * {@link #iconCoverage}), then drawn tinted, so it lands 1:1 on screen pixels like the text does. The ring keeps one
 * texture per size and repaints it only when the fraction it shows moves by a visible amount; a frame that shows
 * the same value draws one quad and allocates nothing.
 *
 * <p>Client thread only. The coverage functions are pure and unit tested.
 */
public final class HudShapes {
   public static final int CHECK = 0;
   public static final int CROSS = 1;
   public static final int INFO = 2;
   public static final int WARNING = 3;
   private static final int ICONS = 4;
   /** Largest icon or ring, in real pixels across, that gets a texture. */
   private static final int MAX_PIXELS = 256;
   /** Half the stroke width of the icons, as a fraction of the icon size. */
   private static final float HALF_STROKE = 0.07F;
   /**
    * The strokes of each icon in a unit square (x right, y down), four numbers per stroke: x1, y1, x2, y2. A stroke
    * whose ends coincide is a dot.
    */
   private static final float[][] STROKES = {
      {0.20F, 0.53F, 0.41F, 0.73F, 0.41F, 0.73F, 0.80F, 0.29F},
      {0.27F, 0.27F, 0.73F, 0.73F, 0.73F, 0.27F, 0.27F, 0.73F},
      {0.50F, 0.25F, 0.50F, 0.25F, 0.50F, 0.46F, 0.50F, 0.77F},
      {0.50F, 0.22F, 0.50F, 0.55F, 0.50F, 0.76F, 0.50F, 0.76F}
   };
   private static final Int2ObjectOpenHashMap<Identifier> ICON_TEXTURES = new Int2ObjectOpenHashMap<>();
   private static final Int2ObjectOpenHashMap<Ring> RINGS = new Int2ObjectOpenHashMap<>();
   private static boolean broken;

   private HudShapes() {
   }

   /**
    * How much of the pixel at ({@code px}, {@code py}) of a {@code size}-pixel icon is inked (0..1): the distance to
    * the nearest stroke against the stroke's half width, antialiased over one pixel. Dots are a little heavier than
    * lines so they read at small sizes.
    */
   static float iconCoverage(int icon, int px, int py, int size) {
      float[] strokes = STROKES[icon];
      float x = (px + 0.5F) / size;
      float y = (py + 0.5F) / size;
      float best = 0.0F;
      for (int i = 0; i < strokes.length; i += 4) {
         float ax = strokes[i];
         float ay = strokes[i + 1];
         float bx = strokes[i + 2];
         float by = strokes[i + 3];
         float dx = bx - ax;
         float dy = by - ay;
         float lengthSq = dx * dx + dy * dy;
         float t = lengthSq <= 0.0F ? 0.0F : Mth.clamp(((x - ax) * dx + (y - ay) * dy) / lengthSq, 0.0F, 1.0F);
         float ox = x - (ax + dx * t);
         float oy = y - (ay + dy * t);
         float half = lengthSq <= 0.0F ? HALF_STROKE * 1.25F : HALF_STROKE;
         float coverage = Mth.clamp((half - (float)Math.sqrt(ox * ox + oy * oy)) * size + 0.5F, 0.0F, 1.0F);
         best = Math.max(best, coverage);
      }

      return best;
   }

   /**
    * How much of the pixel at ({@code px}, {@code py}) lies in a ring of outer radius {@code radius} and width
    * {@code stroke} (both in pixels) centred in a {@code 2 * radius} square, ignoring the angle.
    */
   static float ringCoverage(int px, int py, int radius, float stroke) {
      double dx = px + 0.5 - radius;
      double dy = py + 0.5 - radius;
      double distance = Math.sqrt(dx * dx + dy * dy);
      double outer = Mth.clamp(radius - distance + 0.5, 0.0, 1.0);
      double inner = Mth.clamp(radius - stroke - distance + 0.5, 0.0, 1.0);
      return (float)Math.max(0.0, outer - inner);
   }

   /** Where the pixel sits around the ring: 0 at twelve o'clock, growing clockwise to just under 1. */
   static float ringAngle(int px, int py, int radius) {
      double dx = px + 0.5 - radius;
      double dy = py + 0.5 - radius;
      double turn = Math.atan2(dx, -dy) / (Math.PI * 2.0);
      return (float)(turn < 0.0 ? turn + 1.0 : turn);
   }

   /**
    * How much of a ring pixel at {@code angle} (0..1 around) is lit when the ring shows {@code fraction}: 1 before
    * the end of the arc, 0 after it, blended across about one pixel of the {@code circumference}. A full ring has no
    * end and an empty one no start.
    */
   static float arcCoverage(float angle, float fraction, float circumference) {
      if (fraction >= 1.0F) {
         return 1.0F;
      }

      if (fraction <= 0.0F) {
         return 0.0F;
      }

      float end = Mth.clamp((fraction - angle) * circumference + 0.5F, 0.0F, 1.0F);
      float start = Mth.clamp(angle * circumference + 0.5F, 0.0F, 1.0F);
      return Math.min(end, start);
   }

   private static int pixels(GuiGraphicsExtractor g, int units) {
      return Math.round(units * Glass.pixelScale(g));
   }

   /** Draws icon {@code icon} ({@link #CHECK}, {@link #CROSS}, {@link #INFO}, {@link #WARNING}) in a {@code size} square at {@code x}, {@code y}. */
   public static void icon(GuiGraphicsExtractor g, int icon, int x, int y, int size, int color) {
      if (icon < 0 || icon >= ICONS || size <= 0 || color >>> 24 == 0) {
         return;
      }

      int pixels = pixels(g, size);
      Identifier texture = broken || pixels < 4 || pixels > MAX_PIXELS ? null : iconTexture(icon, pixels);
      if (texture == null) {
         // No texture (a tiny size, or the texture manager refused): a plain dot keeps the place.
         int inset = Math.max(1, size / 3);
         g.fill(x + inset, y + inset, x + size - inset, y + size - inset, color);
         return;
      }

      g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, pixels, pixels, pixels, pixels, color);
   }

   private static Identifier iconTexture(int icon, int pixels) {
      int key = pixels << 4 | icon;
      Identifier id = ICON_TEXTURES.get(key);
      if (id == null) {
         try {
            NativeImage image = new NativeImage(pixels, pixels, false);
            for (int y = 0; y < pixels; y++) {
               for (int x = 0; x < pixels; x++) {
                  image.setPixel(x, y, Math.round(255.0F * iconCoverage(icon, x, y, pixels)) << 24 | 0xFFFFFF);
               }
            }

            id = Identifier.fromNamespaceAndPath("gemini", "hud/icon_" + icon + "_" + pixels);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Gemini HUD icon", image));
            ICON_TEXTURES.put(key, id);
         } catch (RuntimeException | LinkageError e) {
            broken = true;
            return null;
         }
      }

      return id;
   }

   /**
    * A circular progress ring of outer {@code radius} and width {@code stroke} (GUI units) centred on {@code cx},
    * {@code cy}: {@code fraction} (0..1) of it, clockwise from the top, in {@code color}. Draw a full ring in a
    * quiet colour first for the track. {@code slot} separates rings that are on screen at the same time with
    * different values (each slot keeps its own texture); use 0 for a track or any other full ring.
    */
   public static void ring(GuiGraphicsExtractor g, int slot, int cx, int cy, int radius, int stroke, float fraction, int color) {
      float shown = Mth.clamp(fraction, 0.0F, 1.0F);
      if (radius <= 0 || color >>> 24 == 0 || shown <= 0.0F) {
         return;
      }

      int pixels = pixels(g, radius);
      if (broken || pixels < 3 || pixels * 2 > MAX_PIXELS) {
         return;
      }

      float strokePixels = Math.max(1.0F, stroke * Glass.pixelScale(g));
      int key = (shown >= 1.0F ? 0 : 1 + (slot & 7)) << 20 | pixels << 8 | Math.min(255, Math.round(strokePixels * 4.0F));
      Ring ring = RINGS.get(key);
      if (ring == null) {
         try {
            ring = new Ring(pixels, strokePixels, key);
            RINGS.put(key, ring);
         } catch (RuntimeException | LinkageError e) {
            broken = true;
            return;
         }
      }

      ring.show(shown);
      int size = radius * 2;
      g.blit(RenderPipelines.GUI_TEXTURED, ring.id, cx - radius, cy - radius, 0.0F, 0.0F, size, size, pixels * 2, pixels * 2, pixels * 2, pixels * 2, color);
   }

   /** One ring texture: the radial coverage and the angle of every pixel are worked out once, the arc is repainted on change. */
   private static final class Ring {
      final Identifier id;
      private final DynamicTexture texture;
      private final NativeImage image;
      private final int size;
      private final float circumference;
      private final byte[] coverage;
      private final float[] angle;
      private int shownStep = -1;

      Ring(int pixels, float stroke, int key) {
         this.size = pixels * 2;
         this.circumference = (float)(Math.PI * 2.0 * (pixels - stroke / 2.0F));
         this.coverage = new byte[this.size * this.size];
         this.angle = new float[this.size * this.size];
         for (int y = 0; y < this.size; y++) {
            for (int x = 0; x < this.size; x++) {
               this.coverage[y * this.size + x] = (byte)Math.round(255.0F * ringCoverage(x, y, pixels, stroke));
               this.angle[y * this.size + x] = ringAngle(x, y, pixels);
            }
         }

         this.image = new NativeImage(this.size, this.size, false);
         this.texture = new DynamicTexture(() -> "Gemini HUD ring", this.image);
         this.id = Identifier.fromNamespaceAndPath("gemini", "hud/ring_" + Integer.toHexString(key));
         Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
      }

      /** Repaints the arc when {@code fraction} has moved by at least a pixel along the ring. */
      void show(float fraction) {
         int steps = Math.max(1, Math.round(this.circumference));
         int step = fraction >= 1.0F ? steps : Math.round(fraction * steps);
         if (step == this.shownStep) {
            return;
         }

         this.shownStep = step;
         float value = (float)step / steps;
         for (int y = 0; y < this.size; y++) {
            for (int x = 0; x < this.size; x++) {
               int index = y * this.size + x;
               int radial = this.coverage[index] & 0xFF;
               int alpha = radial == 0 ? 0 : Math.round(radial * arcCoverage(this.angle[index], value, this.circumference));
               this.image.setPixel(x, y, alpha << 24 | 0xFFFFFF);
            }
         }

         this.texture.upload();
      }
   }
}
