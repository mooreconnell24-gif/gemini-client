package gemini.mixin;

import gemini.xray.XrayConfig;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FluidRenderer.class})
public abstract class FluidRendererMixin {
   @Inject(
      method = {"isFaceOccludedByState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void gemini$xrayFluidSides(Direction direction, float height, BlockState state, CallbackInfoReturnable<Boolean> cir) {
      XrayConfig config = XrayConfig.current();
      if (config != null && config.hides() && !config.isVisible(state)) {
         cir.setReturnValue(false);
      }
   }
}
