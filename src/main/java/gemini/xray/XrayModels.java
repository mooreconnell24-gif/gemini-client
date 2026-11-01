package gemini.xray;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.sprite.Material.Baked;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class XrayModels {
   private static final ThreadLocal<XrayModels.Styled> STYLED = ThreadLocal.withInitial(XrayModels.Styled::new);

   private XrayModels() {
   }

   public static BlockStateModel style(XrayConfig config, BlockState state, BlockStateModel model) {
      if (config.isTarget(state)) {
         return (BlockStateModel)(config.fullbright() ? STYLED.get().with(model, true, 0) : model);
      } else {
         return (BlockStateModel)(config.hides() ? model : STYLED.get().with(model, false, config.ghostAlpha() << 24 | 16777215));
      }
   }

   private static final class Styled implements BlockStateModel, QuadTransform {
      private BlockStateModel delegate;
      private boolean bright;
      private int ghostTint;

      XrayModels.Styled with(BlockStateModel delegate, boolean bright, int ghostTint) {
         this.delegate = delegate;
         this.bright = bright;
         this.ghostTint = ghostTint;
         return this;
      }

      public boolean transform(MutableQuadView quad) {
         if (this.bright) {
            quad.emissive(true);
            quad.ambientOcclusion(TriState.FALSE);
         }

         if (this.ghostTint != 0) {
            quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
            quad.multiplyColor(this.ghostTint);
         }

         return true;
      }

      public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
         emitter.pushTransform(this);

         try {
            this.delegate.emitQuads(emitter, level, pos, state, random, cullTest);
         } finally {
            emitter.popTransform();
         }
      }

      public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
         this.delegate.collectParts(random, output);
      }

      public Baked particleMaterial() {
         return this.delegate.particleMaterial();
      }

      public int materialFlags() {
         return this.delegate.materialFlags();
      }

      public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
         return this.delegate.createGeometryKey(level, pos, state, random);
      }

      public Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
         return this.delegate.particleMaterial(level, pos, state);
      }

      public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
         return this.delegate.materialFlags(level, pos, state, random);
      }

      public boolean hasMaterialFlag(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, int flag) {
         return this.delegate.hasMaterialFlag(level, pos, state, random, flag);
      }
   }
}
