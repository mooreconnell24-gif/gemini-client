package gemini.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import gemini.module.modules.render.UnfocusedCpu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FramerateLimitTracker.class})
public abstract class FramerateLimitTrackerMixin {
   @Inject(
      method = {"getFramerateLimit"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private void gemini$backgroundCap(CallbackInfoReturnable<Integer> cir) {
      UnfocusedCpu cpu = UnfocusedCpu.instance;
      if (cpu != null && cpu.isEnabled()) {
         cir.setReturnValue(cpu.cap(cir.getReturnValueI()));
      }
   }
}
