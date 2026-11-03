package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class PotionSpoof extends Module {
   private final Map<Holder<MobEffect>, BooleanSetting> effects = new LinkedHashMap<>();
   private final NumberSetting level = this.addSetting(new NumberSetting("Level", "Strength of the faked effects", 1.0, 1.0, 5.0, 1.0));

   public PotionSpoof() {
      super("Potion Spoof", "Apply helpful potion effects on your client.", Category.PLAYER);
      this.add("Night Vision", "See in the dark", MobEffects.NIGHT_VISION, true);
      this.add("Water Breathing", "Show the underwater breathing effect", MobEffects.WATER_BREATHING, false);
      this.add("Fire Resistance", "Show the fire resistance effect", MobEffects.FIRE_RESISTANCE, false);
      this.add("Conduit Power", "Clear underwater vision", MobEffects.CONDUIT_POWER, false);
      this.add("Dolphins Grace", "Swim quickly", MobEffects.DOLPHINS_GRACE, false);
      this.add("Haste", "Mine faster on the client", MobEffects.HASTE, false);
      this.add("Speed", "Move faster on the client", MobEffects.SPEED, false);
      this.add("Jump Boost", "Jump higher on the client", MobEffects.JUMP_BOOST, false);
      this.add("Slow Falling", "Fall slowly on the client", MobEffects.SLOW_FALLING, false);
   }

   private void add(String name, String description, Holder<MobEffect> effect, boolean enabled) {
      this.effects.put(effect, this.addSetting(new BooleanSetting(name, description, enabled)));
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         int amplifier = this.level.intValue() - 1;

         for (Entry<Holder<MobEffect>, BooleanSetting> entry : this.effects.entrySet()) {
            MobEffectInstance current = player.getEffect(entry.getKey());
            boolean ours = current != null && !current.isVisible();
            if (entry.getValue().get()) {
               if (current == null || ours && (current.getDuration() < 200 || current.getAmplifier() != amplifier)) {
                  player.addEffect(new MobEffectInstance(entry.getKey(), 600, amplifier, false, false, false));
               }
            } else if (ours) {
               player.removeEffectNoUpdate(entry.getKey());
            }
         }
      }
   }

   @Override
   public void onDisable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         List<Holder<MobEffect>> mine = new ArrayList<>();

         for (Holder<MobEffect> effect : this.effects.keySet()) {
            MobEffectInstance current = player.getEffect(effect);
            if (current != null && !current.isVisible()) {
               mine.add(effect);
            }
         }

         mine.forEach(player::removeEffectNoUpdate);
      }
   }
}
