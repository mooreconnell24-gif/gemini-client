package com.inferno.fabric.mixin;

import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.checks.ExtraChecks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
   @Unique
   private boolean inferno$offhandTotem;

   @Inject(method = "checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"))
   private void inferno$beforeTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
      LivingEntity self = (LivingEntity)(Object)this;
      this.inferno$offhandTotem = self instanceof ServerPlayer
         && ExtraChecks.AutoTotem.isTotem(self.getOffhandItem())
         && !ExtraChecks.AutoTotem.isTotem(self.getMainHandItem());
   }

   @Inject(method = "checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("RETURN"))
   private void inferno$afterTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
      if (this.inferno$offhandTotem && Boolean.TRUE.equals(cir.getReturnValue())) {
         LivingEntity self = (LivingEntity)(Object)this;
         if (self instanceof ServerPlayer sp) {
            try {
               InfernoMod.ENGINE.totemPop(sp);
            } catch (RuntimeException e) {
               InfernoMod.LOGGER.error("Inferno check error (please report this)", e);
            }
         }
      }

      this.inferno$offhandTotem = false;
   }
}
