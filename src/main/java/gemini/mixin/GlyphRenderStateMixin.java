package gemini.mixin;

import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import gemini.ui.font.SmoothTextures;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GUI glyphs are always sampled with nearest filtering; glyphs of Gemini's smooth UI font use linear filtering
 * instead, so scaled menus and HUD widgets stay smooth. Glyphs of every other font keep the vanilla sampler.
 */
@Mixin({GlyphRenderState.class})
public abstract class GlyphRenderStateMixin {
   @Shadow
   @Final
   private TextRenderable renderable;

   @Redirect(
      method = {"textureSetup"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/renderpearl/api/textures/FilterMode;)Lcom/mojang/renderpearl/api/textures/GpuSampler;"
      ),
      require = 0
   )
   private GpuSampler gemini$smoothUiFont(SamplerCache cache, FilterMode vanilla) {
      return cache.getClampToEdge(SmoothTextures.contains(this.renderable.textureView()) ? FilterMode.LINEAR : vanilla);
   }
}
