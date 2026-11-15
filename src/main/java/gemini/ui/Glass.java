package gemini.ui;

import gemini.Gemini;
import gemini.module.modules.misc.ClickGuiModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The shared look of every Gemini surface (title screen, pause dock, both menus, HUD cards, toasts): dark glass
 * panels with rounded corners, a thin accent border, and exponential-lerp motion.
 *
 * <p>Everything here is static and allocation-free, so it is safe to call from per-frame HUD code. Colours are
 * ARGB ints throughout.
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
   private static final int DEFAULT_ACCENT = 0xFF0070FF;
   private static final int DEFAULT_ACCENT2 = 0xFF00D4FF;
   private static final int DEFAULT_SURFACE = 0xFF0C0F16;

   private Glass() {
   }

   // ---- theme ----------------------------------------------------------------------------------------------

   private static ClickGuiModule style() {
      Gemini gemini = Gemini.getInstance();
      return gemini == null || gemini.modules() == null ? null : gemini.modules().byClass(ClickGuiModule.class).orElse(null);
   }

   public static int accent() {
      ClickGuiModule style = style();
      return style == null ? DEFAULT_ACCENT : style.accentColor();
   }

   public static int accent2() {
      ClickGuiModule style = style();
      return style == null ? DEFAULT_ACCENT2 : style.secondaryColor();
   }

   /** Panel colour; level 0 is the base surface, higher levels are raised (lighter) layers. */
   public static int surface(int level) {
      ClickGuiModule style = style();
      return style == null ? lerp(DEFAULT_SURFACE, 0xFFFFFFFF, level * 0.04F) : style.surface(level);
   }

   public static boolean animations() {
      ClickGuiModule style = style();
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

   /** The pink-to-blue "Astolfo" wave, shifted by {@code offset} (0..1) per element. */
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
    * The standard card: translucent surface, a faint top sheen and a thin accent border. {@code opacity} (0..1)
    * fades the whole card; {@code glow} (0..1) is hover or focus emphasis and brightens the border and adds a halo.
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
      roundRect(g, x1 + 1, y1 + 1, x2 - 1, y1 + Math.max(2, (y2 - y1) / 3), Math.max(0, radius - 1), withAlpha(0xFFFFFF, Math.round((7.0F + 9.0F * emphasis) * opacity)));
      roundOutline(g, x1, y1, x2, y2, radius, withAlpha(lerp(0xFFFFFFFF, accent, 0.55F + 0.45F * emphasis), Math.round((38.0F + 150.0F * emphasis) * opacity)));
   }

   /** As {@link #panel(GuiGraphicsExtractor, int, int, int, int, int, int, int, float, float)} with the theme colours and default radius. */
   public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float opacity, float glow) {
      panel(g, x1, y1, x2, y2, RADIUS, surface(0), accent(), opacity, glow);
   }

   /** Soft halo around a rounded rectangle: three widening rings of falling alpha. */
   public static void glow(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color, float strength) {
      float s = Mth.clamp(strength, 0.0F, 1.0F);
      if (s <= 0.0F) {
         return;
      }

      for (int ring = 3; ring >= 1; ring--) {
         roundOutline(g, x1 - ring, y1 - ring, x2 + ring, y2 + ring, radius + ring, withAlpha(color, Math.round(s * (48.0F / ring))));
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

   public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
      return mx >= x1 && mx < x2 && my >= y1 && my < y2;
   }
}
