package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/** Removes potion effects from you in one click or key press: only the harmful ones by default, or all of them. */
public final class Cleanse extends Module {
   private final BooleanSetting harmfulOnly = this.addSetting(
      new BooleanSetting("Harmful Only", "Keep the good effects (speed, night vision...) and remove only the bad ones", true)
   );

   public Cleanse() {
      super("Cleanse", "Clear harmful (or all) potion effects with one click or key.", Category.PLAYER);
      this.addSetting(new ActionSetting("Cleanse Now", "Remove the effects right away", this::cleanse));
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      this.cleanse();
      this.setEnabled(false, false);
   }

   private void cleanse() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
         return;
      }

      if (!this.harmfulOnly.get()) {
         if (SpCommands.run("effect clear @s")) {
            CommandManager.feedback("§bCleanse §7removed every effect.");
         }

         return;
      }

      // Copied first: the commands below change the player's effect map while we would still be reading it.
      List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
      int removed = 0;
      for (MobEffectInstance instance : active) {
         MobEffect effect = instance.getEffect().value();
         Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         if (effect.getCategory() == MobEffectCategory.HARMFUL && id != null && SpCommands.run("effect clear @s " + id)) {
            removed++;
         }
      }

      CommandManager.feedback(removed == 0 ? "§bCleanse §7found no harmful effects." : "§bCleanse §7removed §f" + removed + " §7harmful effect" + (removed == 1 ? "" : "s") + ".");
   }
}
