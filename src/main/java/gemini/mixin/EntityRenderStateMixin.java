package gemini.mixin;

import gemini.module.modules.render.Chams;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries what Chams decided for an entity from extraction (where the entity is known) to submission (where it is not). */
@Mixin({EntityRenderState.class})
public abstract class EntityRenderStateMixin implements Chams.Marked {
   @Unique
   private int geminiChams;

   @Override
   public int geminiChamsFlags() {
      return this.geminiChams;
   }

   @Override
   public void geminiSetChamsFlags(int flags) {
      this.geminiChams = flags;
   }
}
