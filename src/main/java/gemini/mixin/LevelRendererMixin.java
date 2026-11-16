package gemini.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gemini.module.modules.render.BlockSelection;
import gemini.util.SingleplayerGate;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class LevelRendererMixin {
   /** Block Selection's "Hide Vanilla": its own outline stands in for the vanilla one instead of being drawn over it. */
   @Inject(
      method = {"submitBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$replaceBlockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
      if (BlockSelection.replacesVanilla() && SingleplayerGate.allowed()) {
         ci.cancel();
      }
   }
}
