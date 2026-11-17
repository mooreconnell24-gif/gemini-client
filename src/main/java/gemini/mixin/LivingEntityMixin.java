package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.render.ViewModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin {
   @Inject(
      method = {"getModifiedSwingDuration"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$swingDuration(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
      if (Gemini.getInstance() != null) {
         int duration = ViewModel.swingDuration((Entity)(Object)this);
         if (duration > 0) {
            cir.setReturnValue(duration);
         }
      }
   }
}
