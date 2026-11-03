package gemini.mixin;

import gemini.module.modules.misc.AntiLag;
import gemini.module.modules.render.ParticleBlocker;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleEngine.class})
public abstract class ParticleEngineMixin {
   @Inject(
      method = {"createParticle"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$blockParticle(
      ParticleOptions options, double x, double y, double z, double xa, double ya, double za, CallbackInfoReturnable<Particle> cir
   ) {
      ParticleBlocker blocker = ParticleBlocker.instance;
      if (blocker != null && blocker.isEnabled() && blocker.blocks(options)) {
         cir.setReturnValue(null);
      } else {
         AntiLag antiLag = AntiLag.instance;
         if (antiLag != null && antiLag.shouldBlockParticle(options, x, y, z)) {
            cir.setReturnValue(null);
         }
      }
   }
}
