package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

public final class TntTimer extends Module {
   public TntTimer() {
      super("TNTTimer", "Render a floating fuse countdown above lit TNT.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt && entity.distanceToSqr(client.player) <= 2304.0) {
               float seconds = Math.max(0.0F, (tnt.getFuse() - partialTick) / 20.0F);
               int color = seconds <= 1.2F ? -1548963 : -14249;
               Render3D.text(String.format(Locale.ROOT, "%.2fs", seconds), tnt.getPosition(partialTick).add(0.0, 1.25, 0.0), color, 0.9F, true);
            }
         }
      }
   }
}
