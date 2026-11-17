package gemini.ui.clickgui.controls;

import gemini.ui.Glass;
import gemini.ui.Theme;
import gemini.ui.clickgui.Icons;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The small drawn parts every menu control shares (switch, hover wash, value chip, key chip, scrollbar), all in
 * the colours of {@link Theme#current()} so a theme change restyles them at once. Static and allocation-free.
 */
public final class Widgets {
   public static final int TOGGLE_W = 20;
   public static final int TOGGLE_H = 10;
   /** Horizontal padding between a control's edge and its label or value. */
   public static final int PAD = 8;

   private Widgets() {
   }

   /** Fill of a sunken part: slider tracks, text fields, the bed of a segmented control. */
   public static int well() {
      return Glass.lerp(Glass.surface(0), 0xFF000000, 0.25F);
   }

   /** Fill of a raised part at rest: chips, buttons, the selected segment. */
   public static int raised() {
      return Theme.mix(Glass.surface(3), Glass.text(), 0.07F);
   }

   /** As {@link #raised()} under the mouse. */
   public static int raisedHover() {
      return Theme.mix(Glass.surface(3), Glass.text(), 0.16F);
   }

   /** Track of a switch that is off, and of an empty slider. */
   public static int trackOff() {
      return Theme.mix(Glass.surface(3), Glass.text(), 0.14F);
   }

   /** A hairline between two regions. */
   public static int divider() {
      return Glass.withAlpha(Glass.text(), 16);
   }

   /**
    * An on/off switch, {@link #TOGGLE_W} by {@link #TOGGLE_H}, with its knob at {@code position} (0 = off, 1 = on).
    * {@code enabled} false draws it greyed (a module that cannot run here).
    */
   public static void toggle(GuiGraphicsExtractor g, int x, int y, float position, int accent, boolean enabled, float opacity) {
      if (opacity <= 0.0F) {
         return;
      }

      float p = Mth.clamp(position, 0.0F, 1.0F);
      int on = enabled ? Glass.withAlpha(accent, 255) : Theme.mix(trackOff(), Glass.textFaint(), 0.5F);
      float fade = opacity * (enabled ? 1.0F : 0.7F);
      Glass.roundRect(g, x, y, x + TOGGLE_W, y + TOGGLE_H, TOGGLE_H / 2, Glass.fade(Glass.lerp(trackOff(), on, p), fade));
      int knob = x + 1 + Math.round((TOGGLE_W - TOGGLE_H) * p);
      int knobColor = Glass.lerp(Glass.textDim(), enabled ? Theme.onColor(on) : Glass.text(), p);
      Glass.roundRect(g, knob, y + 1, knob + TOGGLE_H - 2, y + TOGGLE_H - 1, (TOGGLE_H - 2) / 2, Glass.fade(knobColor, fade));
   }

   /** The faint wash behind a hovered row; {@code amount} is the eased hover (0..1). */
   public static void rowHover(GuiGraphicsExtractor g, int x, int y, int w, int h, float amount, float opacity) {
      if (amount > 0.01F) {
         Glass.roundRect(g, x + 2, y, x + w - 2, y + h, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(Glass.text(), Math.round(12.0F * amount)), opacity));
      }
   }

   /** Label colour of a setting: brighter once it differs from its default, so changed settings stand out quietly. */
   public static int label(boolean isDefault) {
      return isDefault ? Glass.textDim() : Glass.text();
   }

   /**
    * A small rounded chip with centred text whose right edge is at {@code right}; returns its left edge.
    * {@code fill} and {@code textColor} are ARGB before {@code opacity}.
    */
   public static int chip(GuiGraphicsExtractor g, String text, int right, int y, int h, int fill, int textColor, float opacity) {
      int w = UiFont.width(text, FontSize.SMALL, FontWeight.MEDIUM) + 10;
      Glass.roundRect(g, right - w, y, right, y + h, Theme.RADIUS_SMALL, Glass.fade(fill, opacity));
      Controls.text(g, text, right - w + 5, UiFont.centerY(y, h, FontSize.SMALL), textColor, opacity, FontSize.SMALL, FontWeight.MEDIUM);
      return right - w;
   }

   /** Width {@link #chip} would take for {@code text}. */
   public static int chipWidth(String text) {
      return UiFont.width(text, FontSize.SMALL, FontWeight.MEDIUM) + 10;
   }

   /** A thin scrollbar thumb for a viewport {@code viewH} tall showing {@code contentH} of content scrolled by {@code scroll}. */
   public static void scrollbar(GuiGraphicsExtractor g, int x, int top, int viewH, int contentH, float scroll, float opacity) {
      if (contentH > viewH && viewH > 8) {
         int thumbH = Math.max(14, Math.round(viewH * ((float)viewH / contentH)));
         float max = Math.max(1, contentH - viewH);
         int thumbY = top + Math.round((viewH - thumbH) * Mth.clamp(scroll / max, 0.0F, 1.0F));
         Glass.roundRect(g, x, thumbY, x + 2, thumbY + thumbH, 1, Glass.fade(Glass.withAlpha(Glass.text(), 56), opacity));
      }
   }

   /** An icon faded by {@code opacity}. */
   public static void icon(GuiGraphicsExtractor g, Icons.Icon icon, int x, int y, int size, int color, float opacity) {
      Icons.draw(g, icon, x, y, size, Glass.fade(color, opacity));
   }
}
