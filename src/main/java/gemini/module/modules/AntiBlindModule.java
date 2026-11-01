package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;

public final class AntiBlindModule extends Module {
   private final BooleanSetting blindness = this.addSetting(new BooleanSetting("Blindness", "Remove the blindness effect on screen", true));
   private final BooleanSetting darkness = this.addSetting(new BooleanSetting("Darkness", "Remove the darkness effect on screen", true));
   private final BooleanSetting nausea = this.addSetting(new BooleanSetting("Nausea", "Remove the wobble from nausea", true));

   public AntiBlindModule() {
      super("AntiBlind", "Clear blindness, darkness and nausea visuals.", Category.RENDER);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (this.blindness.get()) {
            player.removeEffect(MobEffects.BLINDNESS);
         }

         if (this.darkness.get()) {
            player.removeEffect(MobEffects.DARKNESS);
         }

         if (this.nausea.get()) {
            player.removeEffect(MobEffects.NAUSEA);
         }
      }
   }
}
