package gemini.module.modules.render;

import gemini.util.Targets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** Shared helpers for the render modules (formerly statics on the module container classes). */
public final class RenderHelpers {
   private RenderHelpers() {
   }

   static int typeColor(Entity entity) {
      if (entity instanceof Player) {
         return 16735354;
      } else if (entity instanceof ItemEntity) {
         return 16765788;
      } else {
         return entity instanceof LivingEntity living && Targets.isHostile(living) ? 16753212 : 7143306;
      }
   }
}
