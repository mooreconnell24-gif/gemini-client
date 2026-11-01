package gemini.mixin;

import gemini.module.modules.NoRenderModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class HudMixin {
   @Inject(
      method = {"extractVignette"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideVignette()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractPortalOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noPortal(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hidePortal()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractConfusionOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noNausea(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideNausea()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSpyglassOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noSpyglass(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideSpyglass()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractTextureOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noTextureOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideTextureOverlay(texture)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractBossOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noBossBars(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideBossBars()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractScoreboardSidebar"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noScoreboard(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideScoreboard()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractTitle"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noTitle(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideTitles()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractOverlayMessage"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noActionBar(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideActionBar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractEffects"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noEffects(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideEffects()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSelectedItemName"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noHeldName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideHeldName()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSleepOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void gemini$noSleep(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
      NoRenderModule module = NoRenderModule.running();
      if (module != null && module.hideSleep()) {
         ci.cancel();
      }
   }
}
