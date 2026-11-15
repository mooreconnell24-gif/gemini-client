package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import gemini.module.modules.render.ViewModel;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** View Model: per-hand item offsets after the base arm placement, and damped eat and swing motion. */
@Mixin({FirstPersonHandsAndItemsRenderer.class})
public abstract class FirstPersonHandsAndItemsRendererMixin {
   @Inject(
      method = {"applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"},
      at = {@At("TAIL")},
      require = 0
   )
   private void gemini$viewModel(PoseStack pose, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
      ViewModel.applyArm(pose, arm);
   }

   @WrapOperation(
      method = {"submitArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyEatTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/HumanoidArm;FI)V"
      )}
   )
   private void gemini$eatMotion(
      FirstPersonHandsAndItemsRenderer self, PoseStack pose, float partialTick, HumanoidArm arm, float useTicks, int useDuration, Operation<Void> original
   ) {
      ViewModel.scaled(pose, ViewModel.eatFactor(), () -> original.call(self, pose, partialTick, arm, useTicks, useDuration));
   }

   @WrapOperation(
      method = {"submitArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"
      )}
   )
   private void gemini$swingMotion(
      FirstPersonHandsAndItemsRenderer self, float attack, PoseStack pose, int direction, HumanoidArm arm, Operation<Void> original
   ) {
      ViewModel.scaled(pose, ViewModel.swingFactor(), () -> original.call(self, attack, pose, direction, arm));
   }
}
