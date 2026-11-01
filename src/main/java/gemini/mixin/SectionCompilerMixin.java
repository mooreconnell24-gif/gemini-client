package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import gemini.xray.XrayCompile;
import gemini.xray.XrayConfig;
import gemini.xray.XrayModels;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionCompiler.Results;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SectionCompiler.class})
public abstract class SectionCompilerMixin {
   @Inject(
      method = {"compile"},
      at = {@At("HEAD")}
   )
   private void gemini$beginSection(
      SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack builders, CallbackInfoReturnable<Results> cir
   ) {
      XrayCompile.begin(sectionPos);
   }

   @Inject(
      method = {"compile"},
      at = {@At("RETURN")}
   )
   private void gemini$endSection(
      SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack builders, CallbackInfoReturnable<Results> cir
   ) {
      XrayCompile.end();
   }

   @WrapOperation(
      method = {"compile"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
      )}
   )
   private BlockState gemini$filterBlock(RenderSectionRegion region, BlockPos pos, Operation<BlockState> original) {
      BlockState state = (BlockState)original.call(new Object[]{region, pos});
      XrayConfig config = XrayConfig.current();
      return config == null ? state : XrayCompile.filter(config, region, pos, state);
   }

   @WrapOperation(
      method = {"compile"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"
      )}
   )
   private void gemini$styleBlock(
      ModelBlockRenderer renderer,
      BlockQuadOutput output,
      float x,
      float y,
      float z,
      BlockAndTintGetter level,
      BlockPos pos,
      BlockState state,
      BlockStateModel model,
      long seed,
      Operation<Void> original
   ) {
      XrayConfig config = XrayConfig.current();
      original.call(new Object[]{renderer, output, x, y, z, level, pos, state, config == null ? model : XrayModels.style(config, state, model), seed});
   }
}
