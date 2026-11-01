package gemini.mixin;

import gemini.module.modules.misc.MiscModules;
import gemini.module.modules.render.EntityRender;
import gemini.module.modules.render.RenderExtras;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin {
   @Inject(
      method = {"extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V"},
      at = {@At("RETURN")},
      require = 0
   )
   private void gemini$hideVanillaName(
      Entity entity, EntityRenderState state, float partialTicks, double nameTagDistance, double belowNameDistance, CallbackInfo ci
   ) {
      EntityRender.Nametags nametags = EntityRender.Nametags.instance;
      if (nametags != null && nametags.replacesVanilla(entity)) {
         state.nameTag = null;
      } else {
         MiscModules.AntiLag antiLag = MiscModules.AntiLag.instance;
         if (antiLag != null && antiLag.shouldCullNametag(entity, nameTagDistance)) {
            state.nameTag = null;
         } else {
            RenderExtras.EntityCulling culling = RenderExtras.EntityCulling.instance;
            if (culling != null && culling.shouldCullNametag(entity, nameTagDistance)) {
               state.nameTag = null;
            }
         }
      }
   }
}
