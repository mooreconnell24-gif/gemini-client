package gemini.mixin;

import gemini.module.modules.movement.JesusModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LiquidBlock.class})
public abstract class LiquidBlockMixin {
   @Inject(
      method = {"getCollisionShape"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$walkOnFluid(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
      if (JesusModule.active()) {
         VoxelShape shape = JesusModule.surface(state, level, pos, context);
         if (shape != null) {
            cir.setReturnValue(shape);
         }
      }
   }
}
