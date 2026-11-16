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
      super("Potion Timers", "Shows your active effects with their remaining time.", "Right Center", 6, 0, 250);
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

            // Formatted the way the inventory does it: "∞" for infinite, and real seconds at the current tick rate.
            float tickRate = hud.client.level == null ? 20.0F : hud.client.level.tickRateManager().tickrate();
            String time = net.minecraft.world.effect.MobEffectUtil.formatDuration(effect, 1.0F, tickRate).getString();
            MobEffectCategory category = ((MobEffect)effect.getEffect().value()).getCategory();
            int color = category == MobEffectCategory.HARMFUL ? -1548963 : (category == MobEffectCategory.BENEFICIAL ? -11086174 : -1511689);
            lines.add(new TextHudWidget.Line(name + "  " + time, color));
         }

         return lines;
      }
   }
}
