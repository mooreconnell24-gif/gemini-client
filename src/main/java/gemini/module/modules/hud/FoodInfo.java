package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

/** Hunger and saturation as numbers; saturation is what actually decides how long food lasts, and vanilla hides it. */
public final class FoodInfo extends Ticked {
   public FoodInfo() {
      super("Food Info", "Shows your food level and hidden saturation as numbers.", "Top Left", 6, 160, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player == null) {
         return List.of();
      }

      int food = player.getFoodData().getFoodLevel();
      float saturation = player.getFoodData().getSaturationLevel();
      // Amber when you can no longer sprint (6 or less); otherwise plain.
      int color = food <= 6 ? gemini.ui.Glass.WARN : gemini.ui.Glass.TEXT;
      return List.of(
         TextHudWidget.Line.row("Food", Integer.toString(food), "/ 20", color),
         TextHudWidget.Line.row("Saturation", gemini.util.InfoText.format(saturation, 1), "")
      );
   }
}
