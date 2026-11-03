package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class AutoPot extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Throw potion at or below this health", 10.0, 1.0, 20.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between throws", 10.0, 1.0, 60.0, 1.0));
   private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward in packets only", true));
   private final BooleanSetting onlyHealing = this.addSetting(new BooleanSetting("Healing Only", "Use only healing/regen potions", true));
   private int wait;

   public AutoPot() {
      super("Auto Pot", "Splash healing potions at your feet when low on HP.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (!(player.getHealth() > this.health.get())
            && this.wait-- <= 0
            && RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, this.silent.get(), 10, 2.0F)
            && CombatHelpers.withSlot(player, this::usable, () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND)))) {
            this.wait = this.delay.intValue();
         }
      }
   }

   private boolean usable(ItemStack stack) {
      if (!stack.is(Items.SPLASH_POTION)) {
         return false;
      } else {
         PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
         if (contents == null) {
            return false;
         } else {
            for (MobEffectInstance effect : contents.getAllEffects()) {
               String id = effect.getEffect().getRegisteredName();
               if (!this.onlyHealing.get() && ((MobEffect)effect.getEffect().value()).isBeneficial()) {
                  return true;
               }

               if (id.endsWith("instant_health") || id.endsWith("regeneration") || id.endsWith("absorption")) {
                  return true;
               }
            }

            return false;
         }
      }
   }
}
