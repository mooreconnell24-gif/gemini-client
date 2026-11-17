package gemini.ui;

import com.mojang.blaze3d.platform.NativeImage;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.ui.font.UiFont;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The shared look of every Gemini surface (title screen, pause dock, both menus, HUD cards, toasts): dark glass
 * panels with rounded corners, a thin accent border, and exponential-lerp motion.
 *
 * <p>Everything here is static and allocation-free, so it is safe to call from per-frame HUD code. Colours are
 * ARGB ints throughout. Theme colours come from {@link Theme#current()}.
 *
 * <p>Rounded corners and soft shadows are drawn from small mask textures baked on first use at the screen's real
 * pixel density, so a corner is antialiased per screen pixel and costs one quad; where a texture cannot be made the
 * shapes fall back to plain fills.
 */
public final class Glass {
   /** Default corner radius of cards and panels. */
   public static final int RADIUS = 4;
   public static final int TEXT = 0xFFE8ECF4;
   public static final int TEXT_DIM = 0xFF9AA4B8;
   public static final int TEXT_FAINT = 0xFF667088;
   public static final int OK = 0xFF56D68A;
   public static final int WARN = 0xFFF2B84B;
   public static final int ERROR = 0xFFE8605D;
   public static final int INFO = 0xFF5AB0FF;

   private Glass() {
   }

   // ---- theme ----------------------------------------------------------------------------------------------

   /** The theme's primary accent. All theme colours come from {@link Theme#current()}. */
   public static int accent() {
      return Theme.current().accent;
   }

   public static int accent2() {
      return Theme.current().accent2;
   }

   /** Panel colour; level 0 is the base surface, higher levels are raised (lighter) layers. */
   public static int surface(int level) {
      return Theme.current().surface(level);
   }

   /** Primary text colour of the theme (the themed form of {@link #TEXT}). */
   public static int text() {
      return Theme.current().text;
   }

   /** Secondary text colour of the theme (the themed form of {@link #TEXT_DIM}). */
   public static int textDim() {
      return Theme.current().textDim;
   }

   /** Tertiary text colour of the theme (the themed form of {@link #TEXT_FAINT}). */
   public static int textFaint() {
      return Theme.current().textFaint;
   }

   public static boolean animations() {
      ClickGuiModule style = Theme.style();
      return style == null || style.animations();
   }

   // ---- colour ---------------------------------------------------------------------------------------------

   public static int withAlpha(int color, int alpha) {
      return Mth.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
   }

   /** Multiplies the colour's own alpha by {@code factor} (0..1): use it to fade a whole element. */
   public static int fade(int color, float factor) {
      int alpha = Math.round((color >>> 24) * Mth.clamp(factor, 0.0F, 1.0F));
      return alpha << 24 | color & 0xFFFFFF;
   }

   public static int lerp(int from, int to, float t) {
      float c = Mth.clamp(t, 0.0F, 1.0F);
      int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * c);
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * c);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * c);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * c);
      return a << 24 | r << 16 | g << 8 | b;
   }

   /** Opaque colour from hue, saturation and brightness, each 0..1. */
   public static int hsb(float hue, float saturation, float brightness) {
      return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
   }

   /** A rainbow hue that cycles once every {@code periodMs}, shifted by {@code offset} (0..1) per element. */
   public static int rainbow(long periodMs, float offset, float saturation, float brightness) {
      float hue = (Util.getMillis() % periodMs) / (float)periodMs + offset;
      return hsb(hue - (float)Math.floor(hue), saturation, brightness);
   }

   /** The pink-to-blue wave (the ArrayList's "Pink-Blue Wave" colours), shifted by {@code offset} (0..1) per element. */
   public static int astolfo(long periodMs, float offset) {
      float phase = (Util.getMillis() % periodMs) / (float)periodMs + offset;
      phase -= (float)Math.floor(phase);
      float wave = phase < 0.5F ? phase * 2.0F : 2.0F - phase * 2.0F;
      return hsb(0.5F + wave * 0.45F, 0.55F, 1.0F);
   }

   /** 0..1 sine pulse with the given period. */
   public static float pulse(long periodMs) {
      double phase = (Util.getMillis() % periodMs) / (double)periodMs;
      return (float)(0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
   }

   // ---- motion ---------------------------------------------------------------------------------------------

   /**
    * Exponential approach: moves {@code current} toward {@code target} so that the remaining distance shrinks by
    * the same fraction every second, whatever the frame rate. {@code speed} around 12 is brisk, 6 is relaxed.
    * Snaps when close enough, and jumps straight to the target when animations are switched off.
    */
   public static float approach(float current, float target, float speed, float deltaSeconds) {
      if (!animations() || Float.isNaN(current)) {
         return target;
      }

      float next = current + (target - current) * (1.0F - (float)Math.exp(-speed * Math.max(0.0F, deltaSeconds)));
      return Math.abs(target - next) < 0.001F ? target : next;
   }

   public static float easeOutCubic(float t) {
      float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
      return 1.0F - inv * inv * inv;
   }

   public static float easeOutBack(float t) {
      float c = Mth.clamp(t, 0.0F, 1.0F) - 1.0F;
      return 1.0F + 2.70158F * c * c * c + 1.70158F * c * c;
   }

   public static float easeInOut(float t) {
      float c = Mth.clamp(t, 0.0F, 1.0F);
      return c * c * (3.0F - 2.0F * c);
   }

   // ---- shapes ---------------------------------------------------------------------------------------------

   /** Filled rectangle with rounded corners, built from non-overlapping fills so translucent colours stay even. */
   public static void roundRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
      if (x2 <= x1 || y2 <= y1 || color >>> 24 == 0) {
         return;
      }

      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      if (r <= 0) {
         g.fill(x1, y1, x2, y2, color);
         return;
      }

      int pixels = cornerPixels(g, r);
      Identifier disc = pixels < 2 ? null : mask(pixels, 0);
      if (disc != null) {
         // Three bands and four corner quads; nothing overlaps, so translucent colours stay even.
         g.fill(x1 + r, y1, x2 - r, y1 + r, color);
         g.fill(x1, y1 + r, x2, y2 - r, color);
         g.fill(x1 + r, y2 - r, x2 - r, y2, color);
         corners(g, disc, pixels, x1, y1, x2, y2, r, color, color, color, color);
         return;
      }

      g.fill(x1, y1 + r, x2, y2 - r, color);
      for (int i = 0; i < r; i++) {
         int inset = cornerInset(r, i);
         g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
         g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
      }
   }

   /** One-pixel border following the same rounded corners as {@link #roundRect}. */
   public static void roundOutline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
      if (x2 - x1 < 2 || y2 - y1 < 2 || color >>> 24 == 0) {
         return;
      }

      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      if (r <= 0) {
         g.outline(x1, y1, x2 - x1, y2 - y1, color);
         return;
      }

      int pixels = cornerPixels(g, r);
      int stroke = Math.max(1, Math.round(pixels / (float)r));
      Identifier ring = pixels < 2 || stroke >= pixels ? null : mask(pixels, stroke);
      if (ring != null) {
         g.fill(x1 + r, y1, x2 - r, y1 + 1, color);
         g.fill(x1 + r, y2 - 1, x2 - r, y2, color);
         g.fill(x1, y1 + r, x1 + 1, y2 - r, color);
         g.fill(x2 - 1, y1 + r, x2, y2 - r, color);
         corners(g, ring, pixels, x1, y1, x2, y2, r, color, color, color, color);
         return;
      }

      g.fill(x1, y1 + r, x1 + 1, y2 - r, color);
      g.fill(x2 - 1, y1 + r, x2, y2 - r, color);
      int previous = r;
      for (int i = 0; i < r; i++) {
         int inset = cornerInset(r, i);
         // Each corner row covers from this row's inset up to the previous row's, so the arc has no gaps.
         int reach = Math.max(inset + 1, previous);
         if (i == 0) {
            g.fill(x1 + inset, y1, x2 - inset, y1 + 1, color);
            g.fill(x1 + inset, y2 - 1, x2 - inset, y2, color);
         } else {
            g.fill(x1 + inset, y1 + i, x1 + reach, y1 + i + 1, color);
            g.fill(x2 - reach, y1 + i, x2 - inset, y1 + i + 1, color);
            g.fill(x1 + inset, y2 - i - 1, x1 + reach, y2 - i, color);
            g.fill(x2 - reach, y2 - i - 1, x2 - inset, y2 - i, color);
         }

         previous = inset;
      }
   }

   private static int cornerInset(int radius, int row) {
      double dy = radius - row - 0.5;
      return (int)Math.round(radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy)));
   }

   /**
    * The standard card: translucent surface, a faint top sheen and a thin, mostly neutral border. {@code opacity}
    * (0..1) fades the whole card; {@code glow} (0..1) is hover or focus emphasis: it tints the border toward the
    * accent and adds a faint halo. Kept deliberately quiet so a screen full of cards still reads calm.
    */
   public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int surface, int accent, float opacity, float glow) {
      if (opacity <= 0.0F) {
         return;
      }

      float emphasis = Mth.clamp(glow, 0.0F, 1.0F);
      if (emphasis > 0.01F) {
         glow(g, x1, y1, x2, y2, radius, accent, opacity * emphasis);
      }

      roundRect(g, x1, y1, x2, y2, radius, withAlpha(surface, Math.round(214.0F * opacity)));
      roundRect(g, x1 + 1, y1 + 1, x2 - 1, y1 + Math.max(2, (y2 - y1) / 3), Math.max(0, radius - 1), withAlpha(0xFFFFFF, Math.round((5.0F + 3.0F * emphasis) * opacity)));
      roundOutline(g, x1, y1, x2, y2, radius, withAlpha(lerp(0xFFFFFFFF, accent, 0.3F + 0.6F * emphasis), Math.round((30.0F + 80.0F * emphasis) * opacity)));
   }

   /** As {@link #panel(GuiGraphicsExtractor, int, int, int, int, int, int, int, float, float)} with the theme colours and default radius. */
   public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float opacity, float glow) {
      panel(g, x1, y1, x2, y2, RADIUS, surface(0), accent(), opacity, glow);
   }

   /**
    * The HUD card in its default (Soft) look: a borderless, low-alpha tint of {@code surface}, faded by
    * {@code opacity} (0..1). {@code accent} and {@code accent2} are no longer drawn (the card used to carry an accent
    * border); the parameters stay for callers. See {@link HudStyle} for the other looks.
    */
   public static void hudCard(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int surface, int accent, int accent2, float opacity) {
      HudStyle.card(g, x1, y1, x2, y2, HudStyle.SOFT, surface, opacity);
   }

   /** The HUD card in the given {@link HudStyle} backdrop ({@code SOFT}, {@code SOLID} or {@code NONE}). */
   public static void hudCard(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int backdrop, int surface) {
      HudStyle.card(g, x1, y1, x2, y2, backdrop, surface, 1.0F);
   }

   /** Soft halo around a rounded rectangle: three widening rings of falling alpha (faint even at full strength). */
   public static void glow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color, float strength) {
      float s = Mth.clamp(strength, 0.0F, 1.0F);
      if (s <= 0.0F) {
         return;
      }

      for (int ring = 3; ring >= 1; ring--) {
         roundOutline(g, x1 - ring, y1 - ring, x2 + ring, y2 + ring, radius + ring, withAlpha(color, Math.round(s * (22.0F / ring))));
      }
   }

   /** Horizontal two-colour gradient in at most 32 vertical slices. */
   public static void gradientBar(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int from, int to) {
      int width = x2 - x1;
      if (width <= 0 || y2 <= y1) {
         return;
      }

      int steps = Math.min(32, width);
      for (int i = 0; i < steps; i++) {
         int sx = x1 + width * i / steps;
         int ex = x1 + width * (i + 1) / steps;
         g.fill(sx, y1, ex, y2, lerp(from, to, steps <= 1 ? 0.0F : (float)i / (steps - 1)));
      }
   }

   /**
    * A progress or health bar: rounded track with a gradient fill covering {@code fraction} (0..1) of it.
    */
   public static void bar(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float fraction, int track, int from, int to) {
      int radius = Math.min(RADIUS, (y2 - y1) / 2);
      roundRect(g, x1, y1, x2, y2, radius, track);
      int fill = Math.round((x2 - x1) * Mth.clamp(fraction, 0.0F, 1.0F));
      if (fill > 0) {
         g.enableScissor(x1, y1, x1 + fill, y2);
         roundRect(g, x1, y1, x2, y2, radius, from);
         if (from != to) {
            gradientBar(g, x1 + radius, y1, x2 - radius, y2, from, to);
         }

         g.disableScissor();
      }
   }

   /** An on/off switch whose knob sits at {@code position} (0 = off, 1 = on; animate it with {@link #approach}). */
   public static void toggle(GuiGraphicsExtractor g, int x, int y, float position, int accent) {
      float p = Mth.clamp(position, 0.0F, 1.0F);
      int width = 18;
      int height = 10;
      roundRect(g, x, y, x + width, y + height, height / 2, lerp(0xFF2A3040, withAlpha(accent, 255), p));
      int knob = x + 1 + Math.round((width - height) * p);
      roundRect(g, knob, y + 1, knob + height - 2, y + height - 1, (height - 2) / 2, lerp(0xFF8A93A6, 0xFFFFFFFF, p));
   }

   /**
    * As {@link #toggle(GuiGraphicsExtractor, int, int, float, int)}, faded by {@code opacity} (0..1) so the switch
    * follows a menu's open/close fade and opacity setting instead of always drawing at full alpha.
    */
   public static void toggle(GuiGraphicsExtractor g, int x, int y, float position, int accent, float opacity) {
      if (opacity <= 0.0F) {
         return;
      }

      float p = Mth.clamp(position, 0.0F, 1.0F);
      int width = 18;
      int height = 10;
      roundRect(g, x, y, x + width, y + height, height / 2, fade(lerp(0xFF2A3040, withAlpha(accent, 255), p), opacity));
      int knob = x + 1 + Math.round((width - height) * p);
      roundRect(g, knob, y + 1, knob + height - 2, y + height - 1, (height - 2) / 2, fade(lerp(0xFF8A93A6, 0xFFFFFFFF, p), opacity));
   }

   /** A one-pixel straight line between two points at any angle (a rotated fill, so it costs one quad). */
   public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float length = (float)Math.sqrt(dx * dx + dy * dy);
      if (length < 0.5F || color >>> 24 == 0) {
         return;
      }

      g.pose().pushMatrix();
      g.pose().translate(x1, y1);
      g.pose().rotate((float)Math.atan2(dy, dx));
      g.fill(0, 0, Math.round(length), 1, color);
      g.pose().popMatrix();
   }

   // ---- mask textures (smooth corners, soft shadows) -------------------------------------------------------

   /** Largest corner radius, in real pixels, that gets a mask texture; larger corners use fills. */
   private static final int MAX_MASK_PIXELS = 96;
   private static final Int2ObjectOpenHashMap<Identifier> MASKS = new Int2ObjectOpenHashMap<>();
   private static final Int2ObjectOpenHashMap<Identifier> SHADOWS = new Int2ObjectOpenHashMap<>();
   private static boolean masksBroken;

   /** Real pixels per GUI unit under the current pose (GUI scale times pose scale). */
   public static float pixelScale(GuiGraphicsExtractor g) {
      Minecraft client = Minecraft.getInstance();
      int guiScale = client == null || client.getWindow() == null ? 1 : Math.max(1, client.getWindow().getGuiScale());
      return guiScale * UiFont.poseScale(g);
   }

   /** How many real pixels a corner of {@code radius} GUI units spans right now. */
   private static int cornerPixels(GuiGraphicsExtractor g, int radius) {
      return Math.round(radius * pixelScale(g));
   }

   /**
    * Coverage (0..1) of the pixel at ({@code px}, {@code py}) by a disc of {@code radius} centred on
    * ({@code radius}, {@code radius}), minus a concentric hole that leaves a ring {@code stroke} wide (0 = no hole).
    * Antialiased over one pixel. Pure; unit tested.
    */
   static float maskCoverage(int px, int py, int radius, int stroke) {
      double dx = px + 0.5 - radius;
      double dy = py + 0.5 - radius;
      double distance = Math.sqrt(dx * dx + dy * dy);
      double outer = Mth.clamp(radius - distance + 0.5, 0.0, 1.0);
      if (stroke <= 0) {
         return (float)outer;
      }

      double inner = Mth.clamp(radius - stroke - distance + 0.5, 0.0, 1.0);
      return (float)Math.max(0.0, outer - inner);
   }

   /**
    * Darkness (0..1) of a soft shadow at signed distance {@code distance} from the edge of its shape (negative
    * inside), for a blur of {@code blur}: half at the edge, falling smoothly to nothing about 1.5 blurs out.
    * Pure; unit tested.
    */
   static float shadowFalloff(float distance, float blur) {
      if (blur <= 0.0F) {
         return distance <= 0.0F ? 1.0F : 0.0F;
      }

      double sigma = blur / 2.5;
      return (float)(1.0 / (1.0 + Math.exp(1.702 * distance / sigma)));
   }

   /** The white disc (or ring, when {@code stroke} &gt; 0) mask of {@code pixels} radius, or null when unavailable. */
   private static Identifier mask(int pixels, int stroke) {
      if (masksBroken || pixels > MAX_MASK_PIXELS) {
         return null;
      }

      int key = pixels << 8 | stroke;
      Identifier id = MASKS.get(key);
      if (id == null) {
         try {
            int size = pixels * 2;
            NativeImage image = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
               for (int x = 0; x < size; x++) {
                  image.setPixel(x, y, Math.round(255.0F * maskCoverage(x, y, pixels, stroke)) << 24 | 0xFFFFFF);
               }
            }

            id = Identifier.fromNamespaceAndPath("gemini", "glass/mask_" + pixels + "_" + stroke);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Gemini corner mask", image));
            MASKS.put(key, id);
         } catch (RuntimeException | LinkageError e) {
            masksBroken = true;
            return null;
         }
      }

      return id;
   }

   /** The four corner quads of a mask whose quadrants are {@code pixels} texels square, each in its own colour. */
   private static void corners(
      GuiGraphicsExtractor g, Identifier mask, int pixels, int x1, int y1, int x2, int y2, int r, int topLeft, int topRight, int bottomLeft, int bottomRight
   ) {
      int size = pixels * 2;
      g.blit(RenderPipelines.GUI_TEXTURED, mask, x1, y1, 0.0F, 0.0F, r, r, pixels, pixels, size, size, topLeft);
      g.blit(RenderPipelines.GUI_TEXTURED, mask, x2 - r, y1, pixels, 0.0F, r, r, pixels, pixels, size, size, topRight);
      g.blit(RenderPipelines.GUI_TEXTURED, mask, x1, y2 - r, 0.0F, pixels, r, r, pixels, pixels, size, size, bottomLeft);
      g.blit(RenderPipelines.GUI_TEXTURED, mask, x2 - r, y2 - r, pixels, pixels, r, r, pixels, pixels, size, size, bottomRight);
   }

   // ---- gradients ------------------------------------------------------------------------------------------

   /** Smooth top-to-bottom gradient (one quad). */
   public static void gradientV(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom) {
      if (x2 > x1 && y2 > y1) {
         g.fillGradient(x1, y1, x2, y2, top, bottom);
      }
   }

   /** Smooth left-to-right gradient (one quad; unlike {@link #gradientBar} it has no visible steps). */
   public static void gradientH(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int left, int right) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      // A vertical gradient turned a quarter turn: local y runs along screen x.
      g.pose().pushMatrix();
      g.pose().translate(x1, y2);
      g.pose().rotate((float)(-Math.PI / 2.0));
      g.fillGradient(0, 0, y2 - y1, x2 - x1, left, right);
      g.pose().popMatrix();
   }

   /**
    * Rounded rectangle filled with a left-to-right gradient. The gradient runs between the two corner columns, which
    * are flat {@code left} and {@code right}, so there is no seam at the corners.
    */
   public static void roundGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int left, int right) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      int pixels = r <= 0 ? 0 : cornerPixels(g, r);
      Identifier disc = pixels < 2 ? null : mask(pixels, 0);
      if (disc == null) {
         if (r <= 0) {
            gradientH(g, x1, y1, x2, y2, left, right);
         } else {
            g.enableScissor(x1, y1, x2, y2);
            roundRect(g, x1, y1, x1 + (x2 - x1) / 2 + r, y2, r, left);
            roundRect(g, x1 + (x2 - x1) / 2 - r, y1, x2, y2, r, right);
            gradientH(g, x1 + r, y1, x2 - r, y2, left, right);
            g.disableScissor();
         }

         return;
      }

      g.fill(x1, y1 + r, x1 + r, y2 - r, left);
      g.fill(x2 - r, y1 + r, x2, y2 - r, right);
      gradientH(g, x1 + r, y1, x2 - r, y2, left, right);
      corners(g, disc, pixels, x1, y1, x2, y2, r, left, right, left, right);
   }

   /** Rounded rectangle filled with a top-to-bottom gradient (see {@link #roundGradient}). */
   public static void roundGradientV(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int top, int bottom) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      int pixels = r <= 0 ? 0 : cornerPixels(g, r);
      Identifier disc = pixels < 2 ? null : mask(pixels, 0);
      if (disc == null) {
         roundRect(g, x1, y1, x2, y2, r, lerp(top, bottom, 0.5F));
         return;
      }

      g.fill(x1 + r, y1, x2 - r, y1 + r, top);
      g.fill(x1 + r, y2 - r, x2 - r, y2, bottom);
      g.fillGradient(x1, y1 + r, x2, y2 - r, top, bottom);
      corners(g, disc, pixels, x1, y1, x2, y2, r, top, top, bottom, bottom);
   }

   // ---- shadow ---------------------------------------------------------------------------------------------

   /** The default soft drop shadow of a raised card: {@link #shadow(GuiGraphicsExtractor, int, int, int, int, int, int, int, float)} with blur 10, 3 down, 35 % black. */
   public static void shadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius) {
      shadow(g, x1, y1, x2, y2, radius, 10, 3, 0.35F);
   }

   /**
    * Soft drop shadow of a rounded rectangle: draw it before the card. {@code blur} is how far the shadow spreads
    * (GUI units), {@code offsetY} how far it drops, {@code strength} (0..1) its darkness at the card edge times two.
    * Nine quads from a texture baked once per radius and blur.
    */
   public static void shadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int blur, int offsetY, float strength) {
      shadow(g, x1, y1, x2, y2, radius, blur, offsetY, withAlpha(0x000000, Math.round(255.0F * Mth.clamp(strength, 0.0F, 1.0F))));
   }

   /** As {@link #shadow(GuiGraphicsExtractor, int, int, int, int, int, int, int, float)} in any colour (an accent glow, for one). */
   public static void shadow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int blur, int offsetY, int color) {
      if (x2 <= x1 || y2 <= y1 || color >>> 24 == 0 || blur <= 0) {
         return;
      }

      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      int b = Math.min(blur, 32);
      int margin = shadowMargin(b);
      // one slice reaches from the outer margin to just inside the corner
      int inner = Math.min(r + 1, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
      int slice = margin + inner;
      int density = Mth.clamp(Math.round(pixelScale(g)), 1, 2);
      Identifier texture = shadowTexture(r, b, inner, density);
      int top = y1 + offsetY;
      int bottom = y2 + offsetY;
      if (texture == null) {
         // Fallback: four widening rings of falling alpha.
         int alpha = color >>> 24;
         for (int ring = 1; ring <= 4; ring++) {
            int grow = Math.round(margin * ring / 5.0F);
            roundOutline(g, x1 - grow, top - grow, x2 + grow, bottom + grow, r + grow, withAlpha(color, Math.round(alpha * 0.35F * (1.0F - ring / 5.0F))));
         }

         return;
      }

      int texels = slice * density;
      int size = (slice * 2 + 2) * density;
      int ox1 = x1 - margin;
      int oy1 = top - margin;
      int ox2 = x2 + margin;
      int oy2 = bottom + margin;
      int midW = ox2 - ox1 - 2 * slice;
      int midH = oy2 - oy1 - 2 * slice;
      float far = texels + 2.0F * density;
      int stretch = 2 * density;
      // corners
      g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1, oy1, 0.0F, 0.0F, slice, slice, texels, texels, size, size, color);
      g.blit(RenderPipelines.GUI_TEXTURED, texture, ox2 - slice, oy1, far, 0.0F, slice, slice, texels, texels, size, size, color);
      g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1, oy2 - slice, 0.0F, far, slice, slice, texels, texels, size, size, color);
      g.blit(RenderPipelines.GUI_TEXTURED, texture, ox2 - slice, oy2 - slice, far, far, slice, slice, texels, texels, size, size, color);
      // edges and middle, stretched from the two-unit centre strip
      if (midW > 0) {
         g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1 + slice, oy1, texels, 0.0F, midW, slice, stretch, texels, size, size, color);
         g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1 + slice, oy2 - slice, texels, far, midW, slice, stretch, texels, size, size, color);
      }

      if (midH > 0) {
         g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1, oy1 + slice, 0.0F, texels, slice, midH, texels, stretch, size, size, color);
         g.blit(RenderPipelines.GUI_TEXTURED, texture, ox2 - slice, oy1 + slice, far, texels, slice, midH, texels, stretch, size, size, color);
      }

      if (midW > 0 && midH > 0) {
         g.blit(RenderPipelines.GUI_TEXTURED, texture, ox1 + slice, oy1 + slice, texels, texels, midW, midH, stretch, stretch, size, size, color);
      }
   }

   /** How far outside its shape a shadow of {@code blur} is still drawn. */
   static int shadowMargin(int blur) {
      return (int)Math.ceil(blur * 1.5F);
   }

   private static Identifier shadowTexture(int radius, int blur, int inner, int density) {
      if (masksBroken) {
         return null;
      }

      int key = radius | blur << 6 | inner << 12 | density << 20;
      Identifier id = SHADOWS.get(key);
      if (id == null) {
         try {
            int margin = shadowMargin(blur);
            int slice = margin + inner;
            int units = slice * 2 + 2;
            int size = units * density;
            NativeImage image = new NativeImage(size, size, false);
            // the shape: a rounded rectangle inset by the margin
            float half = units / 2.0F - margin;
            for (int y = 0; y < size; y++) {
               for (int x = 0; x < size; x++) {
                  float px = (x + 0.5F) / density - units / 2.0F;
                  float py = (y + 0.5F) / density - units / 2.0F;
                  float qx = Math.abs(px) - (half - radius);
                  float qy = Math.abs(py) - (half - radius);
                  float outside = (float)Math.sqrt(Math.max(qx, 0.0F) * Math.max(qx, 0.0F) + Math.max(qy, 0.0F) * Math.max(qy, 0.0F));
                  float distance = outside + Math.min(Math.max(qx, qy), 0.0F) - radius;
                  image.setPixel(x, y, Math.round(255.0F * shadowFalloff(distance, blur)) << 24 | 0xFFFFFF);
               }
            }

            id = Identifier.fromNamespaceAndPath("gemini", "glass/shadow_" + radius + "_" + blur + "_" + inner + "_" + density);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Gemini shadow", image));
            SHADOWS.put(key, id);
         } catch (RuntimeException | LinkageError e) {
            masksBroken = true;
            return null;
         }
      }

      return id;
   }

   /**
    * A raised card for menus: soft shadow, a solid rounded surface and a hairline border. {@code elevation} (0..1)
    * scales the shadow; 0 draws none.
    */
   public static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int fill, int border, float elevation) {
      if (elevation > 0.0F && fill >>> 24 > 40) {
         shadow(g, x1, y1, x2, y2, radius, 10, 3, 0.4F * Mth.clamp(elevation, 0.0F, 1.0F) * ((fill >>> 24) / 255.0F));
      }

      roundRect(g, x1, y1, x2, y2, radius, fill);
      roundOutline(g, x1, y1, x2, y2, radius, border);
   }

   // ---- blur -----------------------------------------------------------------------------------------------

   /**
    * Blurs what has been drawn so far (the world, or the screen underneath) using the vanilla menu blur. Legal only
    * while a screen is open and only once per frame, so: call it from a screen's background pass, before drawing the
    * menu. It does nothing in the HUD, when the ClickGUI "Blur" switch or the vanilla "Menu Background Blur" option
    * is off, or when something already blurred this frame. Returns whether it blurred.
    */
   public static boolean blurBehind(GuiGraphicsExtractor g) {
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.gui.screen() == null) {
         return false;
      }

      ClickGuiModule style = Theme.style();
      if (style != null && !style.blur() || client.options.getMenuBackgroundBlurriness() < 1) {
         return false;
      }

      try {
         g.blurBeforeThisStratum();
         return true;
      } catch (IllegalStateException alreadyBlurred) {
         return false;
      }
   }

   /**
    * The backdrop of a menu over the game: {@link #blurBehind} and then a dim of {@code dim} (0..1, scaled by the
    * menu's own {@code opacity} so it fades in and out with it) over the whole screen.
    */
   public static void backdrop(GuiGraphicsExtractor g, float dim, float opacity) {
      blurBehind(g);
      int alpha = Math.round(255.0F * Mth.clamp(dim, 0.0F, 1.0F) * Mth.clamp(opacity, 0.0F, 1.0F));
      if (alpha > 0) {
         g.fill(0, 0, g.guiWidth(), g.guiHeight(), withAlpha(lerp(0xFF000000, surface(0), 0.5F), alpha));
      }
   }

   // ---- more motion ----------------------------------------------------------------------------------------

   /** Starts fast, settles very gently: for things arriving (panels opening, toasts sliding in). */
   public static float easeOutQuint(float t) {
      float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
      return 1.0F - inv * inv * inv * inv * inv;
   }

   /** Symmetric and stronger than {@link #easeInOut}: for things that move from one resting place to another. */
   public static float easeInOutCubic(float t) {
      float c = Mth.clamp(t, 0.0F, 1.0F);
      return c < 0.5F ? 4.0F * c * c * c : 1.0F - (float)Math.pow(-2.0F * c + 2.0F, 3.0) / 2.0F;
   }

   /** Accelerating: for things leaving. */
   public static float easeInCubic(float t) {
      float c = Mth.clamp(t, 0.0F, 1.0F);
      return c * c * c;
   }

   /**
    * A damped spring: a value that chases its target with momentum, so it can overshoot a little and settle, which
    * reads as physical. Keep one per animated quantity and call {@link #update} once a frame. The defaults are
    * critically damped (no overshoot); lower {@code damping} below 1 for a soft bounce.
    */
   public static final class Spring {
      public float value;
      public float velocity;
      /** Stiffness: higher reaches the target sooner. Around 170 is brisk, 80 relaxed. */
      public float stiffness = 170.0F;
      /** Damping ratio: 1 = no overshoot, 0.6 = a visible bounce. */
      public float damping = 1.0F;

      public Spring() {
      }

      public Spring(float value) {
         this.value = value;
      }

      public Spring(float value, float stiffness, float damping) {
         this.value = value;
         this.stiffness = stiffness;
         this.damping = damping;
      }

      /** Advances by {@code deltaSeconds} toward {@code target} and returns the new value. Snaps when animations are off. */
      public float update(float target, float deltaSeconds) {
         return this.step(target, deltaSeconds, animations());
      }

      /** As {@link #update} with the animation switch passed in (pure: used by the tests). */
      public float step(float target, float deltaSeconds, boolean animate) {
         if (!animate || Float.isNaN(this.value)) {
            this.set(target);
            return target;
         }

         // Semi-implicit Euler in steps of at most 1/120 s keeps a stiff spring stable at low frame rates.
         float remaining = Math.min(Math.max(0.0F, deltaSeconds), 0.25F);
         float friction = 2.0F * this.damping * (float)Math.sqrt(this.stiffness);
         while (remaining > 0.0F) {
            float dt = Math.min(remaining, 1.0F / 120.0F);
            this.velocity += (this.stiffness * (target - this.value) - friction * this.velocity) * dt;
            this.value += this.velocity * dt;
            remaining -= dt;
         }

         if (Math.abs(target - this.value) < 0.0005F && Math.abs(this.velocity) < 0.005F) {
            this.set(target);
         }

         return this.value;
      }

      /** Jumps to {@code target} and stops. */
      public void set(float target) {
         this.value = target;
         this.velocity = 0.0F;
      }

      public boolean settled(float target) {
         return this.value == target && this.velocity == 0.0F;
      }
   }

   /**
    * The eased interaction state of one control, so every button, row and card responds the same way. Keep one per
    * control, call {@link #update} once a frame, then read {@link #hover}, {@link #press} and {@link #focus} (each
    * 0..1) or the helpers below.
    */
   public static final class Hover {
      /** 0 away .. 1 under the mouse. */
      public float hover;
      /** 0 released .. 1 held down; also briefly raised by {@link #flash}. */
      public float press;
      /** 0 .. 1 keyboard focus or "selected". */
      public float focus;
      private long flashedAt = Long.MIN_VALUE;

      /** Advances the state: {@code over} = mouse inside, {@code down} = pressed while inside. */
      public void update(boolean over, boolean down, float deltaSeconds) {
         this.update(over, down, false, deltaSeconds);
      }

      public void update(boolean over, boolean down, boolean focused, float deltaSeconds) {
         this.hover = approach(this.hover, over ? 1.0F : 0.0F, Theme.HOVER_SPEED, deltaSeconds);
         boolean flashing = this.flashedAt != Long.MIN_VALUE && Util.getMillis() - this.flashedAt < Theme.PRESS_MS;
         // pressing in is quick, letting go a little slower
         this.press = approach(this.press, down || flashing ? 1.0F : 0.0F, down || flashing ? 30.0F : 16.0F, deltaSeconds);
         this.focus = approach(this.focus, focused ? 1.0F : 0.0F, Theme.HOVER_SPEED, deltaSeconds);
      }

      /** Shows a short press pulse for a click that was not held (keyboard activation, a completed click). */
      public void flash() {
         this.flashedAt = Util.getMillis();
      }

      /** Overall emphasis 0..1 (the {@link Glass#panel} glow argument): hover lights it, a press lights it further. */
      public float emphasis() {
         return Mth.clamp(Math.max(this.hover, this.focus) * 0.8F + this.press * 0.6F, 0.0F, 1.0F);
      }

      /** A fill colour: {@code rest} when idle, {@code hovered} under the mouse, a little darker again while pressed. */
      public int fill(int rest, int hovered) {
         int lit = lerp(rest, hovered, Math.max(this.hover, this.focus));
         return this.press <= 0.0F ? lit : lerp(lit, rest, this.press * 0.5F);
      }

      /** A label colour: dim at rest, full while hovered or focused (see {@link Theme#labelColor}). */
      public int label(boolean enabled) {
         return enabled ? lerp(textDim(), text(), 0.35F + 0.65F * Math.max(this.hover, this.focus)) : textFaint();
      }

      /** Scale of the control's content: 1 at rest, shrinking by 3 % while pressed, for a press-in feel. */
      public float scale() {
         return 1.0F - 0.03F * this.press;
      }

      public void reset() {
         this.hover = 0.0F;
         this.press = 0.0F;
         this.focus = 0.0F;
         this.flashedAt = Long.MIN_VALUE;
      }
   }

   public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
      return mx >= x1 && mx < x2 && my >= y1 && my < y2;
   }
}
