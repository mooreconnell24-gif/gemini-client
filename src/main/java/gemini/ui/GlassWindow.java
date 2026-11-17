package gemini.ui;

import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * The shared chrome of Gemini's smaller screens (the guide, world backups, notes, the keybind report), so they all
 * look like one family: the game blurred and dimmed behind (or the home screen's backdrop when no world is
 * loaded), one rounded window with a soft shadow and a hairline edge, a header with the title in the large UI
 * font and a dim line under it, text tabs with a soft fill on the active one, and a thin rounded scrollbar.
 *
 * <p>Keep one instance per screen: it also carries the screen's eased opening ({@link #open}) and frame time.
 */
public final class GlassWindow {
   /** Corner radius of the window. */
   public static final int RADIUS = 8;
   /** Padding between the window's edge and its content. */
   public static final int PAD = 12;
   /** Height of the header (title, the line under it, and the space below). */
   public static final int HEADER = 46;
   /** Height of the header when there is no line under the title. */
   public static final int HEADER_COMPACT = 28;
   /** Height of the footer row that holds the buttons. */
   public static final int FOOTER = Theme.BUTTON_HEIGHT_SMALL + 2 * Theme.SPACE_L;
   /** Height of a text tab and of a list row. */
   public static final int TAB_HEIGHT = 16;
   public static final int ROW_HEIGHT = 16;
   private float open;
   private float delta;
   private long lastFrame;

   /**
    * Advances the frame clock and the opening fade; call once at the start of the background pass. Returns the
    * seconds since the previous frame (0 on the first, at most 0.1).
    */
   public float frame() {
      long now = Util.getMillis();
      this.delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.open = Glass.animations() ? Glass.approach(this.open, 1.0F, Theme.FADE_SPEED, this.delta) : 1.0F;
      return this.delta;
   }

   /** Seconds since the previous frame, as measured by {@link #frame}. */
   public float delta() {
      return this.delta;
   }

   /** How far the window has faded in, 0..1. */
   public float open() {
      return this.open;
   }

   /**
    * The background pass: in a world, the game blurred and dimmed; before one is loaded, the home screen's
    * backdrop, dimmed, so a screen opened from the home screen sits over the picture the player just left.
    */
   public void backdrop(GuiGraphicsExtractor g, int width, int height, int mouseX, int mouseY) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         GeminiTitleScreen.extractBackdrop(g, width, height, mouseX, mouseY, this.delta);
      }

      Glass.backdrop(g, client.level == null ? 0.35F : 0.45F, this.open);
   }

   /** The window itself: shadow, surface and hairline edge. Its content is drawn on top with {@link #open} as opacity. */
   public void frame(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
      float a = this.open;
      Glass.shadow(g, x1, y1, x2, y2, RADIUS, 18, 6, 0.5F * a);
      Glass.roundRect(g, x1, y1, x2, y2, RADIUS, Glass.fade(Glass.withAlpha(Glass.surface(0), 244), a));
      Glass.roundOutline(g, x1, y1, x2, y2, RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, 20), a));
   }

   /**
    * The header inside a window whose top-left corner is ({@code x1}, {@code y1}): the title, and under it
    * {@code subtitle} in {@code subtitleColor} (null or empty for none). Both are ellipsized to {@code x2}.
    */
   public void header(GuiGraphicsExtractor g, int x1, int y1, int x2, String title, String subtitle, int subtitleColor) {
      float a = this.open;
      int room = x2 - x1 - 2 * PAD;
      String shownTitle = UiFont.ellipsize(title, room, FontSize.LARGE, FontWeight.BOLD);
      UiFont.draw(g, shownTitle, x1 + PAD, y1 + PAD, Glass.fade(Glass.text(), a), FontSize.LARGE, FontWeight.BOLD, false);
      if (subtitle != null && !subtitle.isEmpty()) {
         UiFont.draw(g, UiFont.ellipsize(subtitle, room), x1 + PAD, y1 + PAD + UiFont.capHeight(FontSize.LARGE) + 8, Glass.fade(subtitleColor, a), false);
      }
   }

   /** A hairline across the window from {@code x1} to {@code x2}, e.g. between the content and the footer. */
   public void divider(GuiGraphicsExtractor g, int x1, int x2, int y) {
      g.fill(x1, y, x2, y + 1, Glass.fade(Glass.withAlpha(0xFFFFFF, 16), this.open));
   }

   /**
    * One text tab. The active tab has a soft fill and full-strength text; the others are dim and brighten under
    * the mouse ({@code hover} 0..1).
    */
   public void tab(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean active, float hover) {
      float a = this.open;
      if (active) {
         Glass.roundRect(g, x, y, x + w, y + TAB_HEIGHT, TAB_HEIGHT / 2, Glass.fade(Glass.withAlpha(Glass.accent(), 44), a));
      } else if (hover > 0.02F) {
         Glass.roundRect(g, x, y, x + w, y + TAB_HEIGHT, TAB_HEIGHT / 2, Glass.fade(Glass.withAlpha(0xFFFFFF, Math.round(14.0F * hover)), a));
      }

      FontWeight weight = active ? FontWeight.MEDIUM : FontWeight.REGULAR;
      String shown = UiFont.ellipsize(label, w - 6, FontSize.BODY, weight);
      int color = active ? Glass.text() : Glass.lerp(Glass.textDim(), Glass.text(), hover);
      UiFont.drawCentered(g, shown, x + w / 2, UiFont.centerY(y, TAB_HEIGHT), Glass.fade(color, a), FontSize.BODY, weight, false);
   }

   /** The background of a list row: an accent tint when selected, a faint lift under the mouse. */
   public void row(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, boolean selected, boolean hovered) {
      if (selected) {
         Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(Glass.accent(), 50), this.open));
      } else if (hovered) {
         Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, 12), this.open));
      }
   }

   /**
    * A thin scrollbar at {@code x} for a view from {@code top} to {@code bottom} scrolled {@code scroll} of
    * {@code maxScroll}; nothing when the content fits.
    */
   public void scrollbar(GuiGraphicsExtractor g, int x, int top, int bottom, float scroll, float maxScroll) {
      if (maxScroll <= 0.0F || bottom <= top) {
         return;
      }

      int track = bottom - top;
      int thumb = thumbSize(track, maxScroll);
      int thumbY = top + Math.round((track - thumb) * Math.max(0.0F, Math.min(1.0F, scroll / maxScroll)));
      Glass.roundRect(g, x, top, x + 2, bottom, 1, Glass.fade(Glass.withAlpha(0xFFFFFF, 14), this.open));
      Glass.roundRect(g, x, thumbY, x + 2, thumbY + thumb, 1, Glass.fade(Glass.withAlpha(0xFFFFFF, 80), this.open));
   }

   /**
    * {@link UiFont#wrap} for text that may be cut short: when the last line was cut, it ends in exactly one
    * ellipsis (the font's own wrap can leave two when the cut line has room to spare).
    */
   public static List<String> wrap(String text, int maxWidth, int maxLines, FontSize size, FontWeight weight) {
      List<String> lines = new ArrayList<>(UiFont.wrap(text, maxWidth, maxLines, size, weight));
      if (!lines.isEmpty()) {
         int last = lines.size() - 1;
         lines.set(last, singleEllipsis(lines.get(last), UiFont.ellipsis()));
      }

      return lines;
   }

   /** {@code line} with a doubled trailing {@code ellipsis} reduced to one. */
   static String singleEllipsis(String line, String ellipsis) {
      String out = line;
      while (!ellipsis.isEmpty() && out.endsWith(ellipsis + ellipsis)) {
         out = out.substring(0, out.length() - ellipsis.length());
      }

      return out;
   }

   /** Length of the scrollbar thumb for a {@code track} long view with {@code maxScroll} more content: proportional, at least 14. */
   public static int thumbSize(int track, float maxScroll) {
      if (track <= 0) {
         return 0;
      }

      return Math.min(track, Math.max(14, Math.round(track * (track / (track + Math.max(0.0F, maxScroll))))));
   }
}
