package gemini.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The one visual language of the HUD widgets, the watermark and the toasts.
 *
 * <p>A widget sits on a <b>backdrop</b> chosen once for the whole HUD (the HUD module's "Backdrop" setting):
 * <ul>
 *    <li>{@link #SOFT}: a low-alpha tint of the theme surface with rounded corners and no border. Enough to keep text
 *    readable over snow and sky, light enough that the world still shows through.</li>
 *    <li>{@link #SOLID}: the same shape, nearly opaque, for people who want crisp cards.</li>
 *    <li>{@link #NONE}: no panel at all; text is always drawn with a shadow and secondary text is brightened.</li>
 * </ul>
 * Type has three levels: a value is white ({@link Glass#TEXT}), its label dim ({@link #label}), its unit fainter
 * ({@link #unit}). Colour is reserved for a value crossing a threshold ({@link #level}).
 *
 * <p>All methods are static and allocation-free; the pure ones are unit tested.
 */
public final class HudStyle {
   public static final int SOFT = 0;
   public static final int SOLID = 1;
   public static final int NONE = 2;
   /** The backdrop names, in the order of the constants above (the HUD module's setting values). */
   public static final String[] BACKDROPS = {"Soft", "Solid", "None"};
   /** Inner padding of a card: left and right, top and bottom. */
   public static final int PAD_X = Theme.SPACE_M;
   public static final int PAD_Y = Theme.SPACE_S;
   /** Line pitch of text rows. */
   public static final int LINE = Theme.LINE_TIGHT;
   /** Space between a label column and its value column. */
   public static final int LABEL_GAP = Theme.SPACE_L;
   /** Space between a value and its unit. */
   public static final int UNIT_GAP = 3;
   /** Corner radius of every HUD card. */
   public static final int RADIUS = Theme.RADIUS;
   /** Corner radius of small inner shapes (key caps, slots). */
   public static final int RADIUS_SMALL = Theme.RADIUS_SMALL;
   static final int SOFT_ALPHA = 118;
   static final int SOLID_ALPHA = 222;
   /** Label (secondary) text on a card, and brightened for the shadow-only look. */
   static final int LABEL = Glass.TEXT_DIM;
   static final int LABEL_BARE = 0xFFC2CAD8;
   /** Unit (tertiary) text: between dim and faint so it stays legible over a soft card on snow. */
   static final int UNIT = 0xFF7C869A;
   static final int UNIT_BARE = 0xFFA6AFC0;
   /** Thin bar track: a whisper of white on a card, a dark line with no card. */
   static final int TRACK = 0x2CFFFFFF;
   static final int TRACK_BARE = 0x70000000;

   private HudStyle() {
   }

   /** The backdrop constant for a setting value; unknown names are {@link #SOFT}. */
   public static int backdrop(String name) {
      for (int i = 0; i < BACKDROPS.length; i++) {
         if (BACKDROPS[i].equals(name)) {
            return i;
         }
      }

      return SOFT;
   }

   /** Secondary text colour for the backdrop. */
   public static int label(int backdrop) {
      return backdrop == NONE ? LABEL_BARE : LABEL;
   }

   /** Tertiary text colour (units, separators, hints) for the backdrop. */
   public static int unit(int backdrop) {
      return backdrop == NONE ? UNIT_BARE : UNIT;
   }

   /** Whether text is drawn with a shadow: always without a card, otherwise as the HUD's Text Shadow switch says. */
   public static boolean shadow(int backdrop, boolean setting) {
      return backdrop == NONE || setting;
   }

   /**
    * Colour of a value that has a healthy range ({@code ratio} 1 = full, 0 = empty): plain white while it is fine,
    * amber below 30 %, red below 12 %. Colour appears only when it means something.
    */
   public static int level(float ratio) {
      if (ratio >= 0.3F) {
         return Glass.TEXT;
      }

      return ratio >= 0.12F ? Glass.WARN : Glass.ERROR;
   }

   /** Alpha of a colour for the backdrop {@code mode} at {@code opacity} (0..1); 0 for {@link #NONE}. */
   static int cardAlpha(int mode, float opacity) {
      int base = mode == SOLID ? SOLID_ALPHA : (mode == SOFT ? SOFT_ALPHA : 0);
      return Math.round(base * Mth.clamp(opacity, 0.0F, 1.0F));
   }

   /** The card behind a widget: rounded, borderless, tinted with the theme {@code surface}. */
   public static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int mode, int surface, float opacity) {
      card(g, x1, y1, x2, y2, RADIUS, mode, surface, opacity);
   }

   public static void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int mode, int surface, float opacity) {
      int alpha = cardAlpha(mode, opacity);
      if (alpha <= 0 || x2 <= x1 || y2 <= y1) {
         return;
      }

      Glass.roundRect(g, x1, y1, x2, y2, radius, Glass.withAlpha(surface, alpha));
      if (mode == SOLID && y2 - y1 > 6) {
         // A one-pixel lift along the top edge: reads as a surface, not as a border.
         g.fill(x1 + radius, y1, x2 - radius, y1 + 1, Glass.withAlpha(0xFFFFFF, Math.round(14.0F * opacity)));
      }
   }

   /** A thin bar: a quiet track with {@code fraction} (0..1) of it filled in {@code color}. Square ends; it is 1-3 px tall. */
   public static void bar(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float fraction, int color, int backdrop, float opacity) {
      if (x2 <= x1 || y2 <= y1) {
         return;
      }

      int track = backdrop == NONE ? TRACK_BARE : TRACK;
      g.fill(x1, y1, x2, y2, Glass.fade(track, opacity));
      int fill = fillWidth(x2 - x1, fraction);
      if (fill > 0) {
         g.fill(x1, y1, x1 + fill, y2, Glass.fade(color, opacity));
      }
   }

   /** Whole pixels of a {@code width}-pixel bar covered by {@code fraction}; a sliver that is not empty shows at least one. */
   public static int fillWidth(int width, float fraction) {
      if (width <= 0 || !(fraction > 0.0F)) {
         return 0;
      }

      return Math.max(1, Math.min(width, Math.round(width * fraction)));
   }

   /**
    * How visible something at {@code x} is on a strip {@code width} wide whose last {@code margin} pixels at each end
    * fade out (1 inside, falling smoothly to 0 at the ends).
    */
   public static float edgeFade(float x, float width, float margin) {
      if (margin <= 0.0F) {
         return x < 0.0F || x > width ? 0.0F : 1.0F;
      }

      float d = Math.min(x, width - x) / margin;
      if (d <= 0.0F) {
         return 0.0F;
      }

      return d >= 1.0F ? 1.0F : Glass.easeInOut(d);
   }

   /** A horizontal strip in {@code color} whose ends fade out over {@code margin} pixels (the compass backdrop). */
   public static void fadedStrip(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int margin, int color) {
      int width = x2 - x1;
      if (width <= 0 || y2 <= y1 || color >>> 24 == 0) {
         return;
      }

      int m = Math.max(1, Math.min(margin, width / 2));
      int steps = Math.min(16, m);
      int baseAlpha = color >>> 24;
      // Both fading ends in slices; the middle in one fill.
      for (int i = 0; i < steps; i++) {
         int s = m * i / steps;
         int e = m * (i + 1) / steps;
         float f = Glass.easeInOut((i + 0.5F) / steps);
         int c = Glass.withAlpha(color, Math.round(baseAlpha * f));
         g.fill(x1 + s, y1, x1 + e, y2, c);
         g.fill(x2 - e, y1, x2 - s, y2, c);
      }

      g.fill(x1 + m, y1, x2 - m, y2, color);
   }

   /**
    * A key cap: a rounded tile on the backdrop that fills with light as {@code press} goes 0 to 1, its label
    * turning dark for contrast. {@code label} may be null (then only the cap is drawn).
    */
   public static void keycap(
      GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String label, float press, int backdrop, int surface, boolean shadow
   ) {
      float p = Mth.clamp(press, 0.0F, 1.0F);
      int rest = backdrop == NONE ? Glass.withAlpha(surface, 0) : Glass.withAlpha(surface, backdrop == SOLID ? SOLID_ALPHA : SOFT_ALPHA + 20);
      Glass.roundRect(g, x, y, x + w, y + h, RADIUS_SMALL, rest);
      if (p > 0.0F) {
         Glass.roundRect(g, x, y, x + w, y + h, RADIUS_SMALL, Glass.withAlpha(0xFFF2F5FA, Math.round(225.0F * p)));
      }

      if (label != null) {
         int color = Glass.lerp(Glass.TEXT, 0xFF141821, p);
         g.text(font, label, x + (w - font.width(label) + 1) / 2, y + (h - 8) / 2 + 1, color, shadow && p < 0.5F);
      }
   }
}
