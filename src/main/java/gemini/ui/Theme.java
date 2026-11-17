package gemini.ui;

import gemini.Gemini;
import gemini.module.modules.misc.ClickGuiModule;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The one spacing, radius, typography and interaction-state scale every Gemini surface uses (title screen, pause
 * dock, both menus, the palette, the help screen, HUD cards and toasts), and the <b>colour themes</b>.
 *
 * <p><b>Colours.</b> {@link #current()} is the one place to read them from: an accent pair, four surface levels,
 * three text levels and the status colours, as a {@link Palette}. It follows the ClickGUI module's Theme setting
 * (a named preset from {@link #PRESETS}, or Custom built from that module's own colour settings) and crossfades
 * over {@link #FADE_MS} when the theme is switched. {@link Glass} and the HUD read the same palette.
 *
 * <p>The sizes and the hover / press / disabled rules are constants or plain arithmetic, so they are safe in
 * per-frame code and testable; reading the live palette allocates nothing.
 */
public final class Theme {
   // ---- spacing (multiples of 2 px in GUI coordinates) ------------------------------------------------------
   public static final int SPACE_XS = 2;
   public static final int SPACE_S = 4;
   public static final int SPACE_M = 6;
   public static final int SPACE_L = 8;
   public static final int SPACE_XL = 12;
   /** Gap between a screen edge and the first card on it. */
   public static final int EDGE = 6;

   // ---- corner radii ----------------------------------------------------------------------------------------
   /** Chips, badges, key caps and other small inline pills. */
   public static final int RADIUS_SMALL = 3;
   /** Cards, panels, buttons, toasts and HUD widgets. */
   public static final int RADIUS = Glass.RADIUS;
   /** The large outer frame of a full screen card (palette, help screen). */
   public static final int RADIUS_LARGE = 6;

   // ---- typography ------------------------------------------------------------------------------------------
   /** Height of one line of the vanilla font, without leading. */
   public static final int FONT_HEIGHT = 8;
   /** Line pitch of body text and list rows. */
   public static final int LINE = 11;
   /** Line pitch of compact text (status bars, HUD lines). */
   public static final int LINE_TIGHT = 10;
   /** Scale of the heading font (headings are the vanilla font drawn larger). */
   public static final float HEADING_SCALE = 1.5F;

   // ---- control sizes ---------------------------------------------------------------------------------------
   public static final int BUTTON_HEIGHT = 20;
   public static final int BUTTON_HEIGHT_SMALL = 16;
   public static final int ROW_HEIGHT = 18;
   /** Horizontal padding inside a button around its label. */
   public static final int BUTTON_PAD = 8;

   // ---- interaction states ----------------------------------------------------------------------------------
   /** Opacity multiplier of a disabled control (its card and its text). */
   public static final float DISABLED_OPACITY = 0.55F;
   /** How long the press flash of a button lasts. */
   public static final long PRESS_MS = 160L;
   /** Exponential-approach speed of hover highlights. */
   public static final float HOVER_SPEED = 14.0F;
   /** Exponential-approach speed of fades and slides. */
   public static final float FADE_SPEED = 10.0F;

   private Theme() {
   }

   // ---- colour themes ---------------------------------------------------------------------------------------

   /** Name of the theme that uses the ClickGUI module's own Primary, Secondary and Surface settings. */
   public static final String CUSTOM = "Custom";
   /** The theme a fresh install starts with. */
   public static final String DEFAULT = "Gemini";
   /** How long a theme change crossfades. */
   public static final long FADE_MS = 220L;
   private static final int OK = 0xFF56D68A;
   private static final int WARN = 0xFFF2B84B;
   private static final int ERROR = 0xFFE8605D;
   private static final int INFO = 0xFF5AB0FF;

   /**
    * The named themes, in menu order. Each is an accent pair on its own tinted near-black surface; the text tones are
    * neutral greys leaning a little towards the accent. All are checked for contrast by the unit tests (text and dim
    * text at least 4.5:1 on every surface level, faint text and both accents at least 3:1).
    */
   public static final List<Palette> PRESETS = List.of(
      preset("Gemini", 0xFF2E86FF, 0xFF22D3FF, 0xFF0B0E16, 0xFF1B2130),
      preset("Aurora", 0xFF3DDBB4, 0xFF8EE36B, 0xFF091213, 0xFF17272A),
      preset("Ocean", 0xFF4DB8FF, 0xFF7C9CFF, 0xFF09111D, 0xFF17253A),
      preset("Mint", 0xFF45E0A0, 0xFF2CC9F0, 0xFF0A1210, 0xFF1A2824),
      preset("Ember", 0xFFFF8A4C, 0xFFFFC94D, 0xFF140F0C, 0xFF2B211B),
      preset("Sunset", 0xFFFF7A5C, 0xFFFF5C9A, 0xFF150E14, 0xFF2C1F2A),
      preset("Rose", 0xFFFF6B93, 0xFFFFA3BC, 0xFF150D11, 0xFF2D1D25),
      preset("Violet", 0xFFA585FF, 0xFFFF7ADB, 0xFF100D1A, 0xFF241F36),
      preset("Mono", 0xFFDDE2EA, 0xFF8C95A6, 0xFF0D0E10, 0xFF202226),
      preset("Midnight", 0xFF6C88FF, 0xFFA79BFF, 0xFF05060A, 0xFF141722)
   );
   private static final Palette LIVE = new Palette(DEFAULT);
   private static final Palette FROM = new Palette(DEFAULT);
   private static final Palette TARGET = new Palette(DEFAULT);
   private static ClickGuiModule styleModule;
   private static String shownTheme;
   private static String shownSurface;
   private static long fadeStart = Long.MIN_VALUE;
   private static long lastRefresh = Long.MIN_VALUE;

   static {
      LIVE.set(PRESETS.get(0));
      TARGET.set(PRESETS.get(0));
   }

   /** The colours of one theme. Presets never change; the palette returned by {@link #current()} is updated in place. */
   public static final class Palette {
      private String name;
      /** Primary accent: focus, the selected thing, the primary action. */
      public int accent;
      /** Secondary accent: the far end of accent gradients. */
      public int accent2;
      private final int[] surfaces = new int[4];
      /** Primary text. */
      public int text;
      /** Secondary text: labels, descriptions. */
      public int textDim;
      /** Tertiary text: hints, units, disabled labels. */
      public int textFaint;
      public int ok;
      public int warn;
      public int error;
      public int info;

      Palette(String name) {
         this.name = name;
         this.ok = OK;
         this.warn = WARN;
         this.error = ERROR;
         this.info = INFO;
      }

      public String name() {
         return this.name;
      }

      /** Panel colour for an elevation level: 0 is the base, 3 the most raised control. Out-of-range levels are clamped. */
      public int surface(int level) {
         return this.surfaces[Math.max(0, Math.min(3, level))];
      }

      /** Copies every colour (and the name) of {@code other} into this palette. */
      void set(Palette other) {
         this.name = other.name;
         this.accent = other.accent;
         this.accent2 = other.accent2;
         System.arraycopy(other.surfaces, 0, this.surfaces, 0, 4);
         this.text = other.text;
         this.textDim = other.textDim;
         this.textFaint = other.textFaint;
         this.ok = other.ok;
         this.warn = other.warn;
         this.error = other.error;
         this.info = other.info;
      }

      /** Makes this palette the blend of {@code from} and {@code to} at {@code t} (0..1); takes the name of {@code to}. */
      void blend(Palette from, Palette to, float t) {
         this.name = to.name;
         this.accent = mix(from.accent, to.accent, t);
         this.accent2 = mix(from.accent2, to.accent2, t);
         for (int i = 0; i < 4; i++) {
            this.surfaces[i] = mix(from.surfaces[i], to.surfaces[i], t);
         }

         this.text = mix(from.text, to.text, t);
         this.textDim = mix(from.textDim, to.textDim, t);
         this.textFaint = mix(from.textFaint, to.textFaint, t);
         this.ok = mix(from.ok, to.ok, t);
         this.warn = mix(from.warn, to.warn, t);
         this.error = mix(from.error, to.error, t);
         this.info = mix(from.info, to.info, t);
      }

      /** Fills this palette from an accent pair and the lowest and highest surface level. */
      void derive(int accent, int accent2, int surface0, int surface3) {
         this.accent = 0xFF000000 | accent;
         this.accent2 = 0xFF000000 | accent2;
         for (int i = 0; i < 4; i++) {
            this.surfaces[i] = mix(0xFF000000 | surface0, 0xFF000000 | surface3, i / 3.0F);
         }

         this.text = mix(0xFFEBEEF3, this.accent, 0.04F);
         this.textDim = mix(0xFFA3ABBB, this.accent, 0.06F);
         this.textFaint = mix(0xFF78818F, this.accent, 0.06F);
      }
   }

   private static Palette preset(String name, int accent, int accent2, int surface0, int surface3) {
      Palette palette = new Palette(name);
      palette.derive(accent, accent2, surface0, surface3);
      return palette;
   }

   /** The names of the presets followed by {@link #CUSTOM}: the values of the ClickGUI module's Theme setting. */
   public static String[] themeNames() {
      String[] names = new String[PRESETS.size() + 1];
      for (int i = 0; i < PRESETS.size(); i++) {
         names[i] = PRESETS.get(i).name();
      }

      names[PRESETS.size()] = CUSTOM;
      return names;
   }

   /** The preset called {@code name} (any case), or null: Custom and unknown names have no preset. */
   public static Palette preset(String name) {
      if (name != null) {
         for (int i = 0; i < PRESETS.size(); i++) {
            Palette palette = PRESETS.get(i);
            if (palette.name().equalsIgnoreCase(name)) {
               return palette;
            }
         }
      }

      return null;
   }

   /**
    * The current name for a theme name saved by an older version: names that still exist are returned as they are
    * (in their proper case), retired ones map to the closest current theme, anything else to {@link #DEFAULT}.
    */
   public static String canonicalTheme(String saved) {
      if (saved == null) {
         return DEFAULT;
      }

      String name = saved.trim();
      if (name.equalsIgnoreCase(CUSTOM)) {
         return CUSTOM;
      }

      Palette same = preset(name);
      if (same != null) {
         return same.name();
      }

      return switch (name.toLowerCase(java.util.Locale.ROOT)) {
         case "blue" -> "Gemini";
         case "purple", "twin", "dracula" -> "Violet";
         case "gold" -> "Ember";
         case "nord" -> "Ocean";
         case "sakura" -> "Rose";
         default -> DEFAULT;
      };
   }

   /** The retired theme names that {@link #canonicalTheme} still understands. */
   public static String[] retiredThemeNames() {
      return new String[] {"Blue", "Purple", "Twin", "Gold", "Nord", "Dracula", "Sakura"};
   }

   /**
    * The live colours. During the {@link #FADE_MS} after a theme switch this is the blend of the old and new theme.
    * The same instance is returned every time and updated in place: read its fields, do not keep copies of them
    * across frames.
    */
   public static Palette current() {
      long now = System.nanoTime() / 1_000_000L;
      if (now != lastRefresh) {
         lastRefresh = now;
         refresh(now);
      }

      return LIVE;
   }

   private static void refresh(long now) {
      ClickGuiModule style = style();
      String theme = style == null ? DEFAULT : style.themeName();
      String surface = style == null ? "" : style.surfaceName();
      boolean custom = style != null && CUSTOM.equals(theme);
      boolean switched = !theme.equals(shownTheme) || custom && !surface.equals(shownSurface);
      if (switched) {
         boolean first = shownTheme == null;
         shownTheme = theme;
         shownSurface = surface;
         FROM.set(LIVE);
         fadeStart = first || style == null || !style.animations() ? Long.MIN_VALUE : now;
      }

      if (custom) {
         // Custom colours can change every frame (rainbow sync), so the target is rebuilt, not cached.
         TARGET.derive(style.customPrimary(), style.customSecondary(), style.customSurface(0), style.customSurface(3));
         TARGET.name = CUSTOM;
      } else if (switched) {
         Palette preset = preset(theme);
         TARGET.set(preset == null ? PRESETS.get(0) : preset);
      }

      boolean idle = fadeStart == Long.MIN_VALUE;
      float t = fadeProgress(idle ? 0L : now - fadeStart, idle);
      if (t >= 1.0F) {
         if (switched || custom || !idle) {
            LIVE.set(TARGET);
            fadeStart = Long.MIN_VALUE;
         }
      } else {
         LIVE.blend(FROM, TARGET, t);
      }
   }

   /** Eased progress (0..1) of a crossfade that started {@code elapsedMs} ago; 1 when {@code idle}. */
   static float fadeProgress(long elapsedMs, boolean idle) {
      if (idle || elapsedMs >= FADE_MS) {
         return 1.0F;
      }

      float t = Math.max(0.0F, elapsedMs / (float)FADE_MS);
      return t * t * (3.0F - 2.0F * t);
   }

   /** The ClickGUI module (it holds the look settings), or null before the client has started. */
   public static ClickGuiModule style() {
      ClickGuiModule cached = styleModule;
      if (cached == null) {
         Gemini gemini = Gemini.getInstance();
         if (gemini != null && gemini.modules() != null) {
            cached = gemini.modules().byClass(ClickGuiModule.class).orElse(null);
            styleModule = cached;
         }
      }

      return cached;
   }

   /** Whether the Font setting asks for the smooth font (true before the client has started). */
   public static boolean smoothFont() {
      ClickGuiModule style = style();
      return style == null || style.smoothFont();
   }

   /** Channel-wise blend of two ARGB colours; {@code t} is clamped to 0..1. */
   public static int mix(int from, int to, float t) {
      float c = clamp01(t);
      int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * c);
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * c);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * c);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * c);
      return a << 24 | r << 16 | g << 8 | b;
   }

   /** WCAG relative luminance (0 black .. 1 white) of a colour, ignoring alpha. */
   public static double luminance(int color) {
      return 0.2126 * linear(color >> 16 & 0xFF) + 0.7152 * linear(color >> 8 & 0xFF) + 0.0722 * linear(color & 0xFF);
   }

   private static double linear(int channel) {
      double c = channel / 255.0;
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   /** WCAG contrast ratio (1..21) between two opaque colours. */
   public static double contrast(int a, int b) {
      double la = luminance(a);
      double lb = luminance(b);
      return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
   }

   /** Dark or near-white, whichever reads better on {@code background} (text on an accent-filled button). */
   public static int onColor(int background) {
      return contrast(0xFF0B0E14, background) >= contrast(0xFFF4F6FA, background) ? 0xFF0B0E14 : 0xFFF4F6FA;
   }

   /** Label colour of a control: faint when disabled, otherwise dim easing to full white as it is hovered (0..1). */
   public static int labelColor(boolean enabled, float hover) {
      return enabled ? Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, 0.35F + 0.65F * clamp01(hover)) : Glass.TEXT_FAINT;
   }

   /** Overall opacity of a control inside a container faded to {@code containerAlpha}. */
   public static float controlOpacity(boolean enabled, float containerAlpha) {
      return clamp01(containerAlpha) * (enabled ? 1.0F : DISABLED_OPACITY);
   }

   /**
    * Emphasis (0..1, used as the {@link Glass#panel} glow) for a control that is hovered by {@code hover} and was
    * pressed {@code sincePressMs} ago: hover lights it, a press briefly lights it further.
    */
   public static float emphasis(float hover, long sincePressMs) {
      float press = sincePressMs < 0L || sincePressMs >= PRESS_MS ? 0.0F : 1.0F - (float)sincePressMs / PRESS_MS;
      return clamp01(clamp01(hover) * 0.8F + press * 0.6F);
   }

   /**
    * {@code text} cut down and ended with ".." so that it is no wider than {@code maxWidth} as measured by
    * {@code width}. Returns the text unchanged when it fits, and an empty string when not even ".." fits.
    */
   public static String fit(String text, int maxWidth, ToIntFunction<String> width) {
      if (text == null || text.isEmpty()) {
         return "";
      }

      if (width.applyAsInt(text) <= maxWidth) {
         return text;
      }

      if (width.applyAsInt("..") > maxWidth) {
         return "";
      }

      // Binary search on the prefix length: font widths only grow as characters are added.
      int lo = 0;
      int hi = text.length() - 1;
      while (lo < hi) {
         int mid = (lo + hi + 1) >>> 1;
         if (width.applyAsInt(text.substring(0, mid).stripTrailing() + "..") <= maxWidth) {
            lo = mid;
         } else {
            hi = mid - 1;
         }
      }

      return text.substring(0, lo).stripTrailing() + "..";
   }

   private static float clamp01(float value) {
      return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
   }
}
