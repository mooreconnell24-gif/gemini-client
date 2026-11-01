package gemini.mixin;

import gemini.module.modules.misc.MiscModules;
import gemini.module.modules.render.RenderExtras;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public abstract class SoundEngineMixin {
   @Inject(
      method = {"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$blockSound(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
      Identifier id = instance.getIdentifier();
      MiscModules.SoundBlocker blocker = MiscModules.SoundBlocker.instance;
      if (blocker != null && blocker.isEnabled() && blocker.blocks(id)) {
         cir.setReturnValue(PlayResult.NOT_STARTED);
      } else {
         MiscModules.AntiLag antiLag = MiscModules.AntiLag.instance;
         if (antiLag != null && antiLag.shouldBlockSound(id)) {
            cir.setReturnValue(PlayResult.NOT_STARTED);
         } else {
            RenderExtras.SoundLocator locator = RenderExtras.SoundLocator.instance;
            if (locator != null && locator.isEnabled()) {
               locator.recordSound(id.getPath(), instance.getX(), instance.getY(), instance.getZ());
            }
         }
      }
   }
}
