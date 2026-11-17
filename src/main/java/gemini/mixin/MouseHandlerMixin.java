package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gemini.Gemini;
import gemini.module.modules.render.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom's two mouse hooks: the wheel changes the zoom level while zoomed, and the mouse slows down with the zoom. */
@Mixin({MouseHandler.class})
public abstract class MouseHandlerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   private static ZoomModule gemini$zoom() {
      Gemini gemini = Gemini.getInstance();
      return gemini == null || gemini.modules() == null ? null : gemini.modules().byClass(ZoomModule.class).orElse(null);
   }

   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (window == this.minecraft.getWindow().handle() && this.minecraft.gui.screen() == null && this.minecraft.player != null) {
         ZoomModule zoom = gemini$zoom();
         if (zoom != null && zoom.onScroll(Math.signum(vertical))) {
            ci.cancel();
         }
      }
   }

   /** The first {@code doubleValue()} in turnPlayer unboxes the sensitivity option. */
   @WrapOperation(
      method = {"turnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Double;doubleValue()D",
         ordinal = 0
      )},
      require = 0
   )
   private double gemini$zoomSensitivity(Double sensitivity, Operation<Double> original) {
      double value = original.call(sensitivity);
      ZoomModule zoom = gemini$zoom();
      return zoom == null ? value : value * zoom.sensitivityScale();
   }
}
