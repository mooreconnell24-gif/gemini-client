package gemini.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gemini.module.modules.render.NoRenderModule;
import gemini.xray.XrayConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ScreenEffectRenderer.class})
public abstract class ScreenEffectRendererMixin {
   @Inject(
      method = {"submitFire"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void gemini$noFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideFire()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"submitWater"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void gemini$noWater(PlayerRenderState.WaterOverlay overlay, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideWater()) {
         ci.cancel();
      }
   }

   /** Minecraft 26.3 reads the in-wall overlay from the render state; clear it before it is drawn. */
   @Inject(
      method = {"submit"},
      at = {@At("HEAD")},
      require = 0
   )
   private void gemini$noWallOverlay(
      float partialTick, SubmitNodeCollector collector, PlayerRenderState playerState, CameraRenderState cameraState, boolean hideGui, CallbackInfo ci
   ) {
      XrayConfig xray = XrayConfig.current();
      NoRenderModule module = NoRenderModule.running();
      if (xray != null && xray.hides() || module != null && module.hideWall()) {
         playerState.blockOverlay = null;
      }
   }
}
