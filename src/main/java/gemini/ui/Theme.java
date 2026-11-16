package gemini.ui;

import java.util.function.ToIntFunction;

/**
 * The one spacing, radius, typography and interaction-state scale every Gemini surface uses (title screen, pause
 * dock, both menus, the palette, the help screen, HUD cards and toasts). Colours live in {@link Glass}; this class
 * holds the sizes and the rules for hover / press / disabled, so a button looks and behaves the same wherever it is.
 *
 * <p>Everything except {@link #fit} is a constant or plain arithmetic, so it is safe in per-frame code and testable.
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
