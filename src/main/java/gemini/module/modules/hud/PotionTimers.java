package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

public final class PotionTimers extends Ticked {
   public PotionTimers() {
      super("Potion Timers", "Active status effects with their remaining time.", "Right Center", 6, 0, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      List<MobEffectInstance> effects = new ArrayList<>(hud.client.player.getActiveEffects());
      if (effects.isEmpty()) {
         return List.of();
      } else {
         effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
         List<TextHudWidget.Line> lines = new ArrayList<>(effects.size());

         for (MobEffectInstance effect : effects) {
            String name = ((MobEffect)effect.getEffect().value()).getDisplayName().getString();
            if (effect.getAmplifier() > 0) {
               name = name + " " + HudHelpers.roman(effect.getAmplifier() + 1);
            }

            String time = effect.isInfiniteDuration() ? "inf" : HudHelpers.ticksToClock(effect.getDuration());
            MobEffectCategory category = ((MobEffect)effect.getEffect().value()).getCategory();
            int color = category == MobEffectCategory.HARMFUL ? -1548963 : (category == MobEffectCategory.BENEFICIAL ? -11086174 : -1511689);
            lines.add(new TextHudWidget.Line(name + "  " + time, color));
         }

         return lines;
      }
   }
}
