package gemini.ui.font;

import gemini.ui.Theme;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix3x2fc;

/**
 * All Gemini UI text goes through here. Depending on the ClickGUI module's <b>Font</b> setting it is drawn in the
 * bundled smooth sans-serif (Inter) or in the Minecraft font; callers use the same calls and the same layout for both.
 *
 * <p><b>How the smooth font is drawn.</b> It is a set of ordinary Minecraft TrueType font definitions
 * ({@code assets/gemini/font/ui_<size>_<weight>_x<n>.json}), one per {@link FontSize}, {@link FontWeight} and pixel
 * density {@code n}. A draw picks the definition whose density equals the real pixels per GUI unit at that moment
 * (GUI scale times any pose scale), so glyph bitmaps land 1:1 on screen pixels. The text itself is drawn by the
 * game's own {@link Font} with a {@link Style} that names that font, so it batches, clips and fades like any other
 * text. Characters the font lacks (some arrows and symbols) fall back to the Minecraft font. Where a pose scales
 * text by something that is not a whole number of pixels, the next density up is drawn scaled down with linear
 * filtering (see {@link SmoothTextures}), so it is soft rather than blocky.
 *
 * <p><b>Position.</b> {@code y} is the top of the capital letters, in both modes; {@link #capHeight} tells how tall
 * they are, {@link #centerY} centres a line in a box.
 *
 * <p><b>Cost.</b> Each distinct string is turned into a styled character sequence and measured once, then served
 * from a per-string cache (see {@link TextCache}); a cached draw or measure allocates nothing. Caches are dropped
 * when the font mode or the GUI scale changes; {@link #generation()} changes with them so callers that keep their
 * own measurements know to redo them.
 *
 * <p>Client thread only.
 */
public final class UiFont {
   private static final int CACHE_SIZE = 2048;
   private static final int SIZES = FontSize.values().length;
   private static final int WEIGHTS = FontWeight.values().length;
   private static final Style[] STYLES = new Style[FontVariants.count()];
   @SuppressWarnings("unchecked")
   private static final TextCache<Entry>[] CACHES = new TextCache[SIZES * WEIGHTS];
   private static final IdentityHashMap<Component, Component> COMPONENTS = new IdentityHashMap<>();
   private static final java.util.function.Function<String, Entry> NEW_ENTRY = text -> new Entry();
   private static int generation;
   private static boolean lastSmooth;
   private static int lastGuiScale = -1;
   /** Whether the font files are present: null until first asked. */
   private static Boolean available;

   static {
      for (FontSize size : FontSize.values()) {
         for (FontWeight weight : FontWeight.values()) {
            CACHES[size.ordinal() * WEIGHTS + weight.ordinal()] = new TextCache<>(CACHE_SIZE);
            for (int density = 1; density <= FontVariants.MAX_DENSITY; density++) {
               Identifier id = Identifier.fromNamespaceAndPath(FontVariants.NAMESPACE, FontVariants.path(size, weight, density));
               STYLES[FontVariants.index(size, weight, density)] = Style.EMPTY.withFont(new FontDescription.Resource(id));
            }
         }
      }
   }

   private UiFont() {
   }

   /** One cached string: its styled sequence per density, its width, and the last ellipsized form asked for. */
   private static final class Entry {
      final FormattedCharSequence[] sequences = new FormattedCharSequence[FontVariants.MAX_DENSITY];
      int width = -1;
      /** Widths of the other densities' sequences (0 = not measured), made on first use under a scaled pose. */
      int[] variantWidths;
      int fitWidth = -1;
      String fitted;
   }

   // ---- mode ------------------------------------------------------------------------------------------------

   /** Whether text is currently drawn in the smooth font (the setting is on and the font files are there). */
   public static boolean smooth() {
      return Theme.smoothFont() && available();
   }

   /**
    * A number that changes whenever text metrics may have changed (font mode or GUI scale). Code that stores
    * measured widths compares it to know when to measure again.
    */
   public static int generation() {
      sync();
      return generation;
   }

   private static boolean available() {
      if (available == null) {
         try {
            Identifier file = Identifier.fromNamespaceAndPath(FontVariants.NAMESPACE, "font/inter_regular.ttf");
            available = Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
         } catch (RuntimeException e) {
            available = Boolean.FALSE;
         }
      }

      return available;
   }

   /** Drops every cache when the mode or GUI scale changed since the last call; returns whether smooth is active. */
   private static boolean sync() {
      boolean smooth = smooth();
      int guiScale = guiScale();
      if (smooth != lastSmooth || guiScale != lastGuiScale) {
         lastSmooth = smooth;
         lastGuiScale = guiScale;
         generation++;
         for (TextCache<Entry> cache : CACHES) {
            cache.clear();
         }

         COMPONENTS.clear();
      }

      return smooth;
   }

   private static int guiScale() {
      Minecraft client = Minecraft.getInstance();
      return client == null || client.getWindow() == null ? 1 : Math.max(1, client.getWindow().getGuiScale());
   }

   private static Font font() {
      return Minecraft.getInstance().font;
   }

   /** How much the current pose scales things (1 when it does not). */
   public static float poseScale(GuiGraphicsExtractor g) {
      Matrix3x2fc pose = g.pose();
      float a = pose.m00();
      float b = pose.m01();
      return b == 0.0F ? Math.abs(a) : (float)Math.sqrt(a * a + b * b);
   }

   private static Entry entry(String text, FontSize size, FontWeight weight) {
      return CACHES[size.ordinal() * WEIGHTS + weight.ordinal()].get(text, NEW_ENTRY);
   }

   private static FormattedCharSequence sequence(Entry entry, String text, FontSize size, FontWeight weight, int density) {
      FormattedCharSequence sequence = entry.sequences[density - 1];
      if (sequence == null) {
         Style style = STYLES[FontVariants.index(size, weight, density)];
         // iterateFormatted keeps the legacy colour codes working exactly as they do for plain strings
         sequence = sink -> StringDecomposer.iterateFormatted(text, style, sink);
         entry.sequences[density - 1] = sequence;
      }

      return sequence;
   }

   // ---- drawing ---------------------------------------------------------------------------------------------

   /** Body text, regular weight, no shadow. */
   public static void draw(GuiGraphicsExtractor g, String text, int x, int y, int color) {
      draw(g, text, x, y, color, FontSize.BODY, FontWeight.REGULAR, false);
   }

   /** Body text, regular weight. */
   public static void draw(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean shadow) {
      draw(g, text, x, y, color, FontSize.BODY, FontWeight.REGULAR, shadow);
   }

   public static void draw(GuiGraphicsExtractor g, String text, int x, int y, int color, FontSize size, FontWeight weight) {
      draw(g, text, x, y, color, size, weight, false);
   }

   /**
    * Draws {@code text} with the top of its capitals at {@code y} and its left edge at {@code x}. {@code color} is
    * ARGB. The shadow is a soft dark copy half a GUI unit down-right in smooth mode, the vanilla shadow otherwise.
    */
   public static void draw(GuiGraphicsExtractor g, String text, int x, int y, int color, FontSize size, FontWeight weight, boolean shadow) {
      if (text == null || text.isEmpty()) {
         return;
      }

      Font font = font();
      if (sync()) {
         float poseScale = poseScale(g);
         int density = FontVariants.density(lastGuiScale, poseScale, SmoothTextures.active());
         Entry entry = entry(text, size, weight);
         FormattedCharSequence sequence = sequence(entry, text, size, weight, density);
         if (density == FontVariants.density(lastGuiScale, 1.0F) && poseScale == 1.0F) {
            // the usual case: bitmaps are already 1:1 with the screen
            drawSmooth(g, font, sequence, x, y, color, shadow, density);
            return;
         }

         // Under a scaled pose another rasterisation may be in use, whose advances differ a little from the
         // measured ones, and the scale may not be a whole number of pixels per unit.
         int measured = width(text, size, weight);
         int own = measured;
         if (density != FontVariants.density(lastGuiScale, 1.0F)) {
            if (entry.variantWidths == null) {
               entry.variantWidths = new int[FontVariants.MAX_DENSITY];
            }

            own = entry.variantWidths[density - 1];
            if (own == 0) {
               own = Math.max(1, font.width(sequence));
               entry.variantWidths[density - 1] = own;
            }
         }

         Matrix3x2fc pose = g.pose();
         float snap = pose.m01() == 0.0F && pose.m10() == 0.0F ? FontVariants.snapScale(lastGuiScale * poseScale, density, own, measured) : 0.0F;
         g.pose().pushMatrix();
         if (snap > 0.0F) {
            // Nearly a whole number of pixels per unit: draw the bitmaps exactly 1:1 from a whole-pixel origin. The
            // text comes out a few percent smaller or larger than laid out, and sharp instead of resampled.
            float px = (pose.m00() * x + pose.m20()) * lastGuiScale;
            float py = (pose.m11() * y + pose.m21()) * lastGuiScale;
            g.pose().translate(x, y);
            g.pose().scale(snap, snap);
            g.pose().translate((Math.round(px) - px) / density, (Math.round(py) - py) / density);
         } else {
            // Otherwise stretch it to the measured width so layouts computed with width() still line up.
            g.pose().translate(x, y);
            if (measured > 0 && measured != own) {
               g.pose().scale(measured / (float)own, 1.0F);
            }
         }

         drawSmooth(g, font, sequence, 0, 0, color, shadow, density);
         g.pose().popMatrix();
      } else {
         float scale = size.pixelScale();
         if (scale == 1.0F) {
            g.text(font, text, x, y, color, shadow);
         } else {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(scale, scale);
            g.text(font, text, 0, 0, color, shadow);
            g.pose().popMatrix();
         }
      }
   }

   private static void drawSmooth(GuiGraphicsExtractor g, Font font, FormattedCharSequence sequence, int x, int y, int color, boolean shadow, int density) {
      if (shadow && color >>> 24 != 0) {
         float offset = FontVariants.shadowOffset(density);
         g.pose().pushMatrix();
         g.pose().translate(offset, offset);
         g.text(font, sequence, x, y, FontVariants.shadowColor(color), false);
         g.pose().popMatrix();
      }

      g.text(font, sequence, x, y, color, false);
   }

   /** Body text centred on {@code centerX}. */
   public static void drawCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
      drawCentered(g, text, centerX, y, color, FontSize.BODY, FontWeight.REGULAR, false);
   }

   public static void drawCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int color, boolean shadow) {
      drawCentered(g, text, centerX, y, color, FontSize.BODY, FontWeight.REGULAR, shadow);
   }

   public static void drawCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int color, FontSize size, FontWeight weight, boolean shadow) {
      draw(g, text, centerX - width(text, size, weight) / 2, y, color, size, weight, shadow);
   }

   /** Body text whose right edge is at {@code rightX}. */
   public static void drawRight(GuiGraphicsExtractor g, String text, int rightX, int y, int color) {
      drawRight(g, text, rightX, y, color, FontSize.BODY, FontWeight.REGULAR, false);
   }

   public static void drawRight(GuiGraphicsExtractor g, String text, int rightX, int y, int color, boolean shadow) {
      drawRight(g, text, rightX, y, color, FontSize.BODY, FontWeight.REGULAR, shadow);
   }

   public static void drawRight(GuiGraphicsExtractor g, String text, int rightX, int y, int color, FontSize size, FontWeight weight, boolean shadow) {
      draw(g, text, rightX - width(text, size, weight), y, color, size, weight, shadow);
   }

   /** Body text cut with an ellipsis so it is no wider than {@code maxWidth}. Returns the width actually drawn. */
   public static int drawFitted(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
      return drawFitted(g, text, x, y, maxWidth, color, FontSize.BODY, FontWeight.REGULAR, false);
   }

   public static int drawFitted(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color, FontSize size, FontWeight weight, boolean shadow) {
      String fitted = ellipsize(text, maxWidth, size, weight);
      draw(g, fitted, x, y, color, size, weight, shadow);
      return width(fitted, size, weight);
   }

   /** A text component (for labels that come from the game's language files), body size. */
   public static void draw(GuiGraphicsExtractor g, Component text, int x, int y, int color, boolean shadow) {
      if (text == null) {
         return;
      }

      if (sync()) {
         Component styled = styled(text);
         if (shadow && color >>> 24 != 0) {
            float offset = FontVariants.shadowOffset(FontVariants.density(lastGuiScale, 1.0F));
            g.pose().pushMatrix();
            g.pose().translate(offset, offset);
            g.text(font(), styled, x, y, FontVariants.shadowColor(color), false);
            g.pose().popMatrix();
         }

         g.text(font(), styled, x, y, color, false);
      } else {
         g.text(font(), text, x, y, color, shadow);
      }
   }

   // ---- measuring -------------------------------------------------------------------------------------------

   /** Width of body text in GUI units. */
   public static int width(String text) {
      return width(text, FontSize.BODY, FontWeight.REGULAR);
   }

   /** Width of {@code text} in GUI units at the GUI scale's own density (cached per string). */
   public static int width(String text, FontSize size, FontWeight weight) {
      if (text == null || text.isEmpty()) {
         return 0;
      }

      boolean smooth = sync();
      Entry entry = entry(text, size, weight);
      if (entry.width < 0) {
         if (smooth) {
            int density = FontVariants.density(lastGuiScale, 1.0F);
            entry.width = font().width(sequence(entry, text, size, weight, density));
         } else {
            entry.width = FontVariants.scaledWidth(font().width(text), size.pixelScale());
         }
      }

      return entry.width;
   }

   public static int width(Component text) {
      return text == null ? 0 : font().width(sync() ? styled(text) : text);
   }

   /** Height of a capital letter of {@code size} in the active font. */
   public static int capHeight(FontSize size) {
      return size.capHeight(smooth());
   }

   /** Line pitch of body text. */
   public static int lineHeight() {
      return FontSize.BODY.lineHeight();
   }

   public static int lineHeight(FontSize size) {
      return size.lineHeight();
   }

   /** The y that centres one line of {@code size} in a box starting at {@code top} that is {@code height} tall. */
   public static int centerY(int top, int height, FontSize size) {
      return FontVariants.centerY(top, height, capHeight(size));
   }

   /** As {@link #centerY(int, int, FontSize)} for body text. */
   public static int centerY(int top, int height) {
      return centerY(top, height, FontSize.BODY);
   }

   // ---- fitting ---------------------------------------------------------------------------------------------

   /** The ellipsis of the active font ("…" smooth, ".." Minecraft). */
   public static String ellipsis() {
      return smooth() ? TextFit.ELLIPSIS : TextFit.DOTS;
   }

   /** Body text cut with an ellipsis to fit {@code maxWidth}. */
   public static String ellipsize(String text, int maxWidth) {
      return ellipsize(text, maxWidth, FontSize.BODY, FontWeight.REGULAR);
   }

   /**
    * {@code text} cut with an ellipsis to fit {@code maxWidth}. The result for the last width asked is kept with
    * the string, so fitting the same label to the same column every frame costs one comparison.
    */
   public static String ellipsize(String text, int maxWidth, FontSize size, FontWeight weight) {
      if (text == null || text.isEmpty() || maxWidth <= 0) {
         return "";
      }

      if (width(text, size, weight) <= maxWidth) {
         return text;
      }

      Entry entry = entry(text, size, weight);
      if (entry.fitWidth != maxWidth || entry.fitted == null) {
         entry.fitted = TextFit.ellipsize(text, maxWidth, part -> width(part, size, weight), ellipsis());
         entry.fitWidth = maxWidth;
         // the search above may have filled the cache and emptied it; keep this entry reachable
         Entry current = entry(text, size, weight);
         if (current != entry) {
            current.fitted = entry.fitted;
            current.fitWidth = maxWidth;
         }
      }

      return entry.fitted;
   }

   /** Body text wrapped to {@code maxWidth}; at most {@code maxLines} lines (0 = no limit). Allocates: keep the result. */
   public static List<String> wrap(String text, int maxWidth, int maxLines) {
      return wrap(text, maxWidth, maxLines, FontSize.BODY, FontWeight.REGULAR);
   }

   public static List<String> wrap(String text, int maxWidth, int maxLines, FontSize size, FontWeight weight) {
      return TextFit.wrap(text, maxWidth, maxLines, part -> width(part, size, weight), ellipsis());
   }

   // ---- components for vanilla widgets ----------------------------------------------------------------------

   /**
    * {@code text} restyled to the body UI font when smooth text is on (unchanged otherwise): hand it to vanilla
    * widgets such as buttons and tooltips so they match. The copy is cached per component instance.
    */
   public static Component styled(Component text) {
      if (text == null || !sync()) {
         return text;
      }

      Component styled = COMPONENTS.get(text);
      if (styled == null) {
         if (COMPONENTS.size() >= 512) {
            COMPONENTS.clear();
         }

         Style style = STYLES[FontVariants.index(FontSize.BODY, FontWeight.REGULAR, FontVariants.density(lastGuiScale, 1.0F))];
         styled = text.copy().withStyle(current -> current.withFont(style.getFont()));
         COMPONENTS.put(text, styled);
      }

      return styled;
   }

   /** A vanilla tooltip whose text is in the UI font (see {@link #styled}); narration keeps the plain text. */
   public static Tooltip tooltip(Component text) {
      return Tooltip.create(styled(text), text);
   }

   /** A literal component in the UI font (see {@link #styled}). */
   public static Component literal(String text) {
      Component plain = Component.literal(text == null ? "" : text);
      if (!sync()) {
         return plain;
      }

      Style style = STYLES[FontVariants.index(FontSize.BODY, FontWeight.REGULAR, FontVariants.density(lastGuiScale, 1.0F))];
      return plain.copy().withStyle(style);
   }
}
