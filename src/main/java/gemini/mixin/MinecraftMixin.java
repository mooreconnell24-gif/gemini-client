package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.FastPlaceModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
public class MinecraftMixin {
   @Shadow
   private int rightClickDelay;

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void gemini$fastPlace(CallbackInfo ci) {
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().modules().byClass(FastPlaceModule.class).ifPresent(module -> {
            if (module.isEnabled()) {
               int limit = module.limit((Minecraft)(Object)this);
               if (this.rightClickDelay > limit) {
                  this.rightClickDelay = limit;
               }
            }
         });
      }
   }
}
