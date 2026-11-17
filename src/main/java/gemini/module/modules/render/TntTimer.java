package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import gemini.util.InfoText;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

public final class TntTimer extends Module {
   /** "1.23s" per hundredth of a second, built the first time each is shown; a fuse is at most a few hundred of them. */
   private String[] labels = new String[0];

   public TntTimer() {
      super("TNT Timer", "Shows a fuse countdown above lit TNT.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt && entity.distanceToSqr(client.player) <= 2304.0) {
               float seconds = Math.max(0.0F, (tnt.getFuse() - partialTick) / 20.0F);
               int color = seconds <= 1.2F ? -1548963 : -14249;
               Render3D.text(
                  this.label(seconds),
                  Render3D.entityX(tnt, partialTick),
                  Render3D.entityY(tnt, partialTick) + 1.25,
                  Render3D.entityZ(tnt, partialTick),
                  color,
                  0.9F,
                  true
               );
            }
         }
      }
   }

   private String label(float seconds) {
      int hundredths = Math.round(seconds * 100.0F);
      if (hundredths < 0 || hundredths > 6_000) {
         return InfoText.format(seconds, 2) + "s";
      }

      if (hundredths >= this.labels.length) {
         this.labels = Arrays.copyOf(this.labels, Math.max(hundredths + 1, Math.max(401, this.labels.length * 2)));
      }

      String text = this.labels[hundredths];
      if (text == null) {
         text = InfoText.format(hundredths / 100.0, 2) + "s";
         this.labels[hundredths] = text;
      }

      return text;
   }
}
