package gemini.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The small line icons of Gemini's menus. The UI font has no icon glyphs, so each icon is drawn from shapes on a
 * 10 x 10 grid: antialiased rings and rounded squares from {@link Glass}, and round-capped strokes (see
 * {@link #stroke}) drawn in window pixels so they stay smooth at every GUI scale.
 */
public enum GlassIcon {
   /** Four tiles: the module menu. */
   MODULES,
   /** An arrow into a tray: downloading mods. */
   MODS,
   /** A page with lines: the guide. */
   GUIDE,
   /** A magnifier: quick search. */
   SEARCH,
   /** A screen with two widgets on it: the HUD editor. */
   HUD,
   /** A power symbol: Panic switches everything off. */
   PANIC,
   /** An exclamation mark in a ring: something needs attention. */
   ALERT,
   /** A chevron pointing right: "opens something". */
   CHEVRON;

   /** Side of the icon's box in GUI units. */
   public static final int SIZE = 10;

   /** Draws the icon with its box's top-left corner at ({@code x}, {@code y}). */
   public void draw(GuiGraphicsExtractor g, int x, int y, int color) {
      if (color >>> 24 < 4) {
         return;
      }

      switch (this) {
         case MODULES -> {
            Glass.roundRect(g, x, y, x + 4, y + 4, 1, color);
            Glass.roundRect(g, x + 6, y, x + 10, y + 4, 1, color);
            Glass.roundRect(g, x, y + 6, x + 4, y + 10, 1, color);
            Glass.roundRect(g, x + 6, y + 6, x + 10, y + 10, 1, color);
         }
         case MODS -> {
            stroke(g, x + 5.0F, y + 0.8F, x + 5.0F, y + 6.2F, 1.2F, color);
            stroke(g, x + 2.4F, y + 3.8F, x + 5.0F, y + 6.4F, 1.2F, color);
            stroke(g, x + 7.6F, y + 3.8F, x + 5.0F, y + 6.4F, 1.2F, color);
            stroke(g, x + 1.0F, y + 9.2F, x + 9.0F, y + 9.2F, 1.2F, color);
         }
         case GUIDE -> {
            Glass.roundOutline(g, x + 1, y, x + 9, y + 10, 2, color);
            g.fill(x + 3, y + 3, x + 7, y + 4, color);
            g.fill(x + 3, y + 6, x + 6, y + 7, color);
         }
         case SEARCH -> {
            Glass.roundOutline(g, x, y, x + 8, y + 8, 4, color);
            stroke(g, x + 6.9F, y + 6.9F, x + 9.3F, y + 9.3F, 1.3F, color);
         }
         case HUD -> {
            Glass.roundOutline(g, x, y + 1, x + 10, y + 9, 2, color);
            g.fill(x + 2, y + 3, x + 5, y + 4, color);
            g.fill(x + 6, y + 6, x + 8, y + 7, color);
         }
         case PANIC -> {
            Glass.roundOutline(g, x + 1, y + 2, x + 9, y + 10, 4, color);
            stroke(g, x + 5.0F, y + 0.6F, x + 5.0F, y + 5.2F, 1.3F, color);
         }
         case ALERT -> {
            Glass.roundOutline(g, x, y, x + 10, y + 10, 5, color);
            stroke(g, x + 5.0F, y + 2.8F, x + 5.0F, y + 5.4F, 1.2F, color);
            stroke(g, x + 5.0F, y + 7.3F, x + 5.0F, y + 7.3F, 1.2F, color);
         }
         case CHEVRON -> {
            stroke(g, x + 3.6F, y + 2.2F, x + 6.4F, y + 5.0F, 1.2F, color);
            stroke(g, x + 6.4F, y + 5.0F, x + 3.6F, y + 7.8F, 1.2F, color);
         }
      }
   }

   /**
    * A straight stroke with round ends between two points in GUI units, {@code thickness} GUI units wide: a chain
    * of small antialiased discs one window pixel apart, drawn in window pixels. For icon-sized strokes only (the
    * number of discs is the stroke's length in pixels).
    */
   public static void stroke(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float thickness, int color) {
      Minecraft client = Minecraft.getInstance();
      int scale = client == null || client.getWindow() == null ? 1 : Math.max(1, client.getWindow().getGuiScale());
      int size = Math.max(2, Math.round(thickness * scale));
      float px1 = x1 * scale;
      float py1 = y1 * scale;
      float dx = x2 * scale - px1;
      float dy = y2 * scale - py1;
      int steps = Math.max(1, Math.round((float)Math.sqrt(dx * dx + dy * dy)));
      int dab = Glass.withAlpha(color, dabAlpha(color >>> 24, Math.min(size, steps + 1)));
      g.pose().pushMatrix();
      g.pose().scale(1.0F / scale, 1.0F / scale);
      for (int i = 0; i <= steps; i++) {
         float t = i / (float)steps;
         int cx = Math.round(px1 + dx * t - size * 0.5F);
         int cy = Math.round(py1 + dy * t - size * 0.5F);
         Glass.roundRect(g, cx, cy, cx + size, cy + size, size / 2, dab);
      }

      g.pose().popMatrix();
   }

   /**
    * Opacity (0..255) of one disc of a stroke so that {@code layers} discs on top of each other add up to
    * {@code alpha}: a translucent stroke would otherwise come out darker where its discs overlap.
    */
   static int dabAlpha(int alpha, int layers) {
      if (alpha >= 255 || layers <= 1) {
         return Math.max(0, Math.min(255, alpha));
      }

      double each = 1.0 - Math.pow(1.0 - Math.max(0, alpha) / 255.0, 1.0 / layers);
      return Math.max(1, (int)Math.round(each * 255.0));
   }
}
