package gemini.mixin;

import gemini.Gemini;
import gemini.module.modules.EspModule;
import gemini.module.modules.combat.CombatModules;
import gemini.module.modules.movement.MovementModules;
import gemini.module.modules.render.CameraModules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Entity.class})
public abstract class EntityMixin {
   @Inject(
      method = {"isCurrentlyGlowing"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$espGlow(CallbackInfoReturnable<Boolean> cir) {
      EspModule esp = esp();
      if (esp != null && esp.shouldGlow((Entity)(Object)this)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"getTeamColor"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$espColor(CallbackInfoReturnable<Integer> cir) {
      EspModule esp = esp();
      if (esp != null && esp.shouldGlow((Entity)(Object)this)) {
         cir.setReturnValue(esp.color((Entity)(Object)this));
      }
   }

   @Inject(
      method = {"turn"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$redirectTurn(double dx, double dy, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (self == Minecraft.getInstance().player) {
         CameraModules.Freecam freecam = CameraModules.Freecam.instance;
         if (freecam != null && freecam.consumeTurn(self, dx, dy)) {
            ci.cancel();
         } else {
            CameraModules.FreeLook look = CameraModules.FreeLook.instance;
            if (look != null && look.consumeTurn(self, dx, dy)) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"getPickRadius"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private void gemini$widenHitbox(CallbackInfoReturnable<Float> cir) {
      CombatModules.Hitboxes hitboxes = CombatModules.Hitboxes.instance;
      if (hitboxes != null && hitboxes.isEnabled()) {
         float extra = hitboxes.extra((Entity)(Object)this);
         if (extra > 0.0F) {
            cir.setReturnValue((Float)cir.getReturnValue() + extra);
         }
      }
   }

   // Full descriptor on purpose: if a future version changes the signature this matches nothing and is skipped
   // (require = 0) instead of failing on a mismatched handler.
   @Inject(
      method = {"makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noWeb(BlockState state, Vec3 multiplier, CallbackInfo ci) {
      if ((Object)this == Minecraft.getInstance().player && MovementModules.NoWeb.cancels(state)) {
         ci.cancel();
      }
   }

   private static EspModule esp() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.modules() != null) {
         EspModule esp = gemini.modules().byClass(EspModule.class).orElse(null);
         return esp != null && esp.isEnabled() ? esp : null;
      } else {
         return null;
      }
   }
}
