package gemini.mixin;

import gemini.module.modules.misc.AntiLag;
import gemini.module.modules.render.Chams;
import gemini.module.modules.render.EntityCulling;
import gemini.module.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
      Nametags nametags = Nametags.instance;
      if (nametags != null && nametags.replacesVanilla(entity)) {
         state.nameTag = null;
      } else {
         AntiLag antiLag = AntiLag.instance;
         if (antiLag != null && antiLag.shouldCullNametag(entity, nameTagDistance)) {
            state.nameTag = null;
         } else {
            EntityCulling culling = EntityCulling.instance;
            if (culling != null && culling.shouldCullNametag(entity, nameTagDistance)) {
               state.nameTag = null;
            }
         }
      }
   }

   /** Marks each freshly extracted state for Chams, and gives the Textured mode's hidden entities their outline. */
   @Inject(
      method = {"createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"},
      at = {@At("RETURN")},
      require = 0
   )
   private void gemini$chamsMark(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
      EntityRenderState state = cir.getReturnValue();
      if (state instanceof Chams.Marked marked) {
         Chams chams = Chams.instance;
         int flags = chams == null ? 0 : chams.flagsFor(entity);
         marked.geminiSetChamsFlags(flags);
         int outline = chams == null ? 0 : chams.outlineFor(flags);
         if (outline != 0) {
            state.outlineColor = outline;
         }
      }
   }
}
