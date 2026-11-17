package gemini.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import gemini.module.modules.render.Chams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chams on living entities: tints the real model's submit and adds the posed-cube overlay next to it. Only the
 * level's own camera counts, so entities drawn in GUI previews (the inventory paper doll) are left alone.
 */
@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin {
   @WrapOperation(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
      )}
   )
   private void gemini$chams(
      SubmitNodeCollector collector,
      Model<?> model,
      Object state,
      PoseStack poseStack,
      RenderType renderType,
      int light,
      int overlay,
      int tint,
      UvMapping uvMapping,
      int outline,
      Operation<Void> original,
      @Local(argsOnly = true) CameraRenderState camera
   ) {
      Chams chams = Chams.instance;
      int flags = chams != null && chams.isEnabled() && state instanceof Chams.Marked marked && isLevelCamera(camera) ? marked.geminiChamsFlags() : 0;
      if (flags == 0) {
         original.call(collector, model, state, poseStack, renderType, light, overlay, tint, uvMapping, outline);
         return;
      }

      original.call(collector, model, state, poseStack, renderType, light, overlay, chams.tint(tint, flags), uvMapping, outline);
      chams.submitCubes(model, state, poseStack, collector, camera, flags);
   }

   private static boolean isLevelCamera(CameraRenderState camera) {
      return camera != null && camera == Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
   }
}
