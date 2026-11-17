package gemini.mixin;

import gemini.ui.font.SmoothTextures;
import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notes which glyph atlases belong to Gemini's smooth UI font, so they can be sampled smoothly (see {@link SmoothTextures}). */
@Mixin({FontTexture.class})
public abstract class FontTextureMixin extends AbstractTexture {
   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")},
      require = 0
   )
   private void gemini$trackUiFont(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
      if (label != null && SmoothTextures.isUiFont(label.get())) {
         SmoothTextures.track(this.textureView);
      }
   }
}
