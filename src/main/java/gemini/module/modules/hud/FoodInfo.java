package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;

/** Hunger and saturation as numbers; saturation is what actually decides how long food lasts, and vanilla hides it. */
public final class FoodInfo extends Ticked {
   public FoodInfo() {
      super("Food Info", "Hunger and hidden saturation as numbers.", "Top Left", 6, 160, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player == null) {
         return List.of();
      }

      int food = player.getFoodData().getFoodLevel();
      float saturation = player.getFoodData().getSaturationLevel();
      int color = food <= 6 ? -43691 : -6641995;
      return List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "Food %d/20  Sat %.1f", food, saturation), color));
   }
}
