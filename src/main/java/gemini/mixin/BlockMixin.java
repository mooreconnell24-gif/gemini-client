package gemini.mixin;

import gemini.module.modules.movement.MovementModules;
import gemini.xray.XrayConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Block.class})
public abstract class BlockMixin {
   @Inject(
      method = {"shouldRenderFace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void gemini$xrayFaces(BlockState state, BlockState neighbor, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      XrayConfig config = XrayConfig.current();
      if (config != null && config.isTarget(state)) {
         cir.setReturnValue(!config.isVisible(neighbor) || !neighbor.isSolidRender());
      }
   }

   @Inject(
      method = {"getFriction"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void gemini$slippy(CallbackInfoReturnable<Float> cir) {
      MovementModules.Slippy slippy = MovementModules.Slippy.instance;
      if (slippy != null && slippy.isEnabled()) {
         cir.setReturnValue(slippy.override((Block)(Object)this, cir.getReturnValue()));
      }
   }
}
