package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gemini.Gemini;
import gemini.module.modules.ZoomModule;
import gemini.module.modules.render.CameraModules;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow
   private boolean detached;

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @Inject(
      method = {"calculateFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void gemini$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
      float fov = cir.getReturnValueF();
      boolean changed = false;
      CameraModules.Fov custom = CameraModules.Fov.instance;
      if (custom != null && custom.isEnabled()) {
         fov = custom.apply(fov);
         changed = true;
      }

      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null) {
         ZoomModule zoom = gemini.modules().byClass(ZoomModule.class).orElse(null);
         if (zoom != null && zoom.isEnabled()) {
            fov = Math.max(4.0F, fov / zoom.multiplier());
            changed = true;
         }
      }

      if (changed) {
         cir.setReturnValue(fov);
      }
   }

   @WrapOperation(
      method = {"alignWithEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"
      )}
   )
   private float gemini$lookYaw(Entity entity, float partialTicks, Operation<Float> original) {
      CameraModules.FreeLook look = CameraModules.FreeLook.instance;
      return look != null && look.active() && entity == Minecraft.getInstance().player ? look.yaw() : (Float)original.call(new Object[]{entity, partialTicks});
   }

   @WrapOperation(
      method = {"alignWithEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"
      )}
   )
   private float gemini$lookPitch(Entity entity, float partialTicks, Operation<Float> original) {
      CameraModules.FreeLook look = CameraModules.FreeLook.instance;
      return look != null && look.active() && entity == Minecraft.getInstance().player
         ? look.pitch()
         : (Float)original.call(new Object[]{entity, partialTicks});
   }

   @Inject(
      method = {"alignWithEntity"},
      at = {@At("RETURN")}
   )
   private void gemini$freecam(float partialTicks, CallbackInfo ci) {
      CameraModules.Freecam freecam = CameraModules.Freecam.instance;
      if (freecam != null && freecam.active()) {
         this.setRotation(freecam.yaw(), freecam.pitch());
         this.setPosition(freecam.x(partialTicks), freecam.y(partialTicks), freecam.z(partialTicks));
         this.detached = true;
      }
   }

   @ModifyArg(
      method = {"alignWithEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"
      )},
      index = 0
   )
   private float gemini$distance(float vanilla) {
      CameraModules.CameraTweaks tweaks = CameraModules.CameraTweaks.instance;
      return tweaks != null && tweaks.isEnabled() ? tweaks.distanceOr(vanilla) : vanilla;
   }

   @Inject(
      method = {"getMaxZoom"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void gemini$clip(float distance, CallbackInfoReturnable<Float> cir) {
      CameraModules.CameraTweaks tweaks = CameraModules.CameraTweaks.instance;
      if (tweaks != null && tweaks.isEnabled() && tweaks.clips()) {
         cir.setReturnValue(distance);
      }
   }
}
