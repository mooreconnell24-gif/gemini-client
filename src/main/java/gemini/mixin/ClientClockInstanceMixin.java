package gemini.mixin;

import gemini.module.modules.render.Ambience;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ambience's client-side time of day. The client keeps one clock per world clock and the sky, the light and the
 * sun and moon all read the time from it, so answering here changes what is drawn and nothing on the server.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClientClockInstanceMixin {
   @Inject(
      method = {"totalTicks"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$ambienceTime(CallbackInfoReturnable<Long> cir) {
      long time = Ambience.timeOverride();
      if (time >= 0L) {
         cir.setReturnValue(time);
      }
   }

   /** A fixed time has no fraction of a tick; without this the sun would still creep within each tick. */
   @Inject(
      method = {"partialTick"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$ambiencePartial(CallbackInfoReturnable<Float> cir) {
      if (Ambience.timeOverride() >= 0L) {
         cir.setReturnValue(0.0F);
      }
   }
}
