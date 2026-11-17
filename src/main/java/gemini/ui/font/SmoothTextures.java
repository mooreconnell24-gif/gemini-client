package gemini.ui.font;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The glyph atlas textures that belong to the smooth UI font. The game samples every font texture with nearest
 * filtering, which is right for pixel fonts and for smooth glyphs drawn 1:1, but turns smooth glyphs into uneven
 * blocks whenever a menu or the HUD is scaled by something that is not a whole number of pixels. So the two mixins
 * {@code FontTextureMixin} (which registers our atlases here as they are created) and {@code GlyphRenderStateMixin}
 * (which asks here when a glyph is drawn) switch just these textures to linear filtering. Other fonts are untouched.
 *
 * <p>Views are held weakly: an atlas dropped on a resource reload leaves no entry behind. Render thread only.
 */
public final class SmoothTextures {
   private static final Set<Object> VIEWS = Collections.newSetFromMap(new WeakHashMap<>());
   private static boolean any;

   private SmoothTextures() {
   }

   /** Whether a font atlas with this debug label (its texture name) belongs to a Gemini UI font. */
   public static boolean isUiFont(String label) {
      return label != null && label.contains(FontVariants.NAMESPACE + ":") && label.contains("ui_");
   }

   /** Registers the texture view of one of our glyph atlases. */
   public static void track(Object textureView) {
      if (textureView != null) {
         VIEWS.add(textureView);
         any = true;
      }
   }

   /** Whether glyphs from this texture view are smooth-font glyphs (and so are sampled with linear filtering). */
   public static boolean contains(Object textureView) {
      return any && textureView != null && VIEWS.contains(textureView);
   }

   /** Whether linear filtering is in effect, i.e. the mixins applied and at least one of our atlases exists. */
   public static boolean active() {
      return any;
   }
}
