package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.FastPlaceModule;
import gemini.util.SingleplayerGate;
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
      at = {@At("HEAD")},
      require = 0
   )
   private void gemini$fastPlace(CallbackInfo ci) {
      Minecraft self = (Minecraft)(Object)this;
      SingleplayerGate.tick(self);
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().modules().byClass(FastPlaceModule.class).ifPresent(module -> {
            if (module.isEnabled()) {
               int limit = module.limit(self);
               if (this.rightClickDelay > limit) {
                  this.rightClickDelay = limit;
               }
            }
         });
      }
   }
}
