package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class LastDeath extends Ticked {
   private BlockPos position;
   private String dimension = "";
   private boolean dead;

   public LastDeath() {
      super("Last Death", "Where you last died, and how far away that is.", "Top Left", 6, 184, 500);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (player.isDeadOrDying()) {
            if (!this.dead) {
               this.dead = true;
               this.position = player.blockPosition();
               this.dimension = Minecraft.getInstance().level.dimension().identifier().getPath();
            }
         } else {
            this.dead = false;
         }
      }
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      if (this.position == null) {
         return List.of();
      } else {
         boolean sameWorld = hud.client.level.dimension().identifier().getPath().equals(this.dimension);
         String where = this.position.getX() + "  " + this.position.getY() + "  " + this.position.getZ();
         if (!sameWorld) {
            return List.of(new TextHudWidget.Line("Died in " + HudHelpers.pretty(this.dimension), -1548963), new TextHudWidget.Line(where, -6641995));
         } else {
            double distance = Math.sqrt(this.position.distSqr(hud.client.player.blockPosition()));
            return List.of(new TextHudWidget.Line("Died " + Math.round(distance) + "m away", -1548963), new TextHudWidget.Line(where, -6641995));
         }
      }
   }
}
