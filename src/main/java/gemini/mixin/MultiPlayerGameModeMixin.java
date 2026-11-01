package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.InstantMineModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public abstract class MultiPlayerGameModeMixin {
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   private float destroyProgress;
   @Shadow
   private boolean isDestroying;

   @Inject(
      method = {"continueDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gemini$instantMine(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (Gemini.getInstance() != null && this.minecraft.level != null) {
         InstantMineModule module = Gemini.getInstance().modules().byClass(InstantMineModule.class).orElse(null);
         if (module != null && module.isEnabled() && module.breakNow(pos)) {
            this.destroyProgress = 0.0F;
            this.isDestroying = false;
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"attack"},
      at = {@At("HEAD")}
   )
   private void gemini$onAttack(Player player, Entity target, CallbackInfo ci) {
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().modules().attack(target);
      }
   }
}
