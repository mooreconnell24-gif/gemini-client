package gemini.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gemini.module.modules.NoRenderModule;
import gemini.xray.XrayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
   private static void gemini$noWater(Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideWater()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"getViewBlockingState"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void gemini$noWallOverlay(Player player, CallbackInfoReturnable<BlockState> cir) {
      XrayConfig xray = XrayConfig.current();
      if (xray != null && xray.hides()) {
         cir.setReturnValue(null);
      } else {
         NoRenderModule module = NoRenderModule.running();
         if (module != null && module.hideWall()) {
            cir.setReturnValue(null);
         }
      }
   }

   @Inject(
      method = {"displayItemActivation"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noTotemPop(ItemStack stack, RandomSource random, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideTotem() && stack.is(Items.TOTEM_OF_UNDYING)) {
         ci.cancel();
      }
   }
}
