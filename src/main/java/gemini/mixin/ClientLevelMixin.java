package gemini.mixin;

import gemini.Gemini;
import gemini.util.ChunkChanges;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientLevel.class})
public abstract class ClientLevelMixin {
   @Inject(
      method = {"setBlocksDirty"},
      at = {@At("HEAD")},
      require = 0
   )
   private void gemini$trackChanges(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
      ChunkChanges.blockChanged(pos);
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null) {
         gemini.modules().blockUpdate(pos, oldState, newState);
      }
   }
}
