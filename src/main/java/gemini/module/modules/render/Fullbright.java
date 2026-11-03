package gemini.module.modules.render;

import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Fullbright extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Effect gives true fullbright; Gamma raises brightness to the slider maximum", "Effect", "Effect", "Gamma")
   );
   private double previousGamma = 0.5;
   private boolean gammaApplied;

   public Fullbright() {
      super("Fullbright", "See clearly in caves and at night.", Category.RENDER, Keys.fromGlfw('B'));
   }

   @Override
   public void onEnable() {
      this.gammaApplied = false;
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      this.restoreGamma(client);
      LocalPlayer player = client.player;
      if (player != null) {
         MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
         if (effect != null && effect.isInfiniteDuration()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
         }
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (this.mode.is("Gamma")) {
            if (!this.gammaApplied) {
               this.previousGamma = (Double)client.options.gamma().get();
               this.gammaApplied = true;
            }

            client.options.gamma().set(1.0);
            MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
            if (effect != null && effect.isInfiniteDuration()) {
               player.removeEffect(MobEffects.NIGHT_VISION);
            }
         } else {
            this.restoreGamma(client);
            if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
               player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
            }
         }
      }
   }

   private void restoreGamma(Minecraft client) {
      if (this.gammaApplied) {
         client.options.gamma().set(this.previousGamma);
         this.gammaApplied = false;
      }
   }
}
