package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

/** Experience level, progress to the next level in percent, and total points. */
public final class XpInfo extends Ticked {
   public XpInfo() {
      super("Xp Info", "Shows your experience level, progress to the next level and total points.", "Top Left", 6, 172, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player == null) {
         return List.of();
      }

      int percent = Math.round(player.experienceProgress * 100.0F);
      return List.of(
         TextHudWidget.Line.row("Level", Integer.toString(player.experienceLevel), percent + "%"),
         TextHudWidget.Line.row("Total", HudFormat.grouped(player.totalExperience), "xp")
      );
   }
}
