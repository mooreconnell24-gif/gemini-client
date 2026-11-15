package gemini.mixin;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;
import gemini.util.AttackAssist;
import gemini.Gemini;
import gemini.module.modules.movement.SafeWalkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Player.class})
public abstract class PlayerMixin {
   @Inject(
      method = {"isStayingOnGroundSurface"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$safeWalk(CallbackInfoReturnable<Boolean> cir) {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null && (Object)this == Minecraft.getInstance().player) {
         SafeWalkModule safeWalk = gemini.modules().byClass(SafeWalkModule.class).orElse(null);
         if (safeWalk != null && safeWalk.isEnabled()) {
            cir.setReturnValue(true);
         }
      }
   }

   /** Runs for every player on either side; {@link AttackAssist} only acts for the world owner's server-side player. */
   @Inject(method = "attack", at = @At("HEAD"), require = 0)
   private void gemini$attackAssistBefore(Entity target, CallbackInfo ci) {
      AttackAssist.before((Player)(Object)this);
   }

   @Inject(method = "attack", at = @At("RETURN"), require = 0)
   private void gemini$attackAssistAfter(Entity target, CallbackInfo ci) {
      AttackAssist.after((Player)(Object)this);
   }
}
