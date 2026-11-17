package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.InfoText;
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

public final class Buffs extends Module {
   private static final int REFRESH_TICKS = 200;
   private final List<Buffs.Buff> buffs = new ArrayList<>();
   private final BooleanSetting hideParticles = this.addSetting(new BooleanSetting("Hide Particles", "Give the effects without the swirling particles", true));
   private final BooleanSetting autoCleanse = this.addSetting(
      new BooleanSetting("Auto Cleanse", "Remove harmful effects such as poison, wither and slowness as soon as you get them", false)
   );
   private int refresh;
   private int cleanseWait;
   private boolean applying;
   private final InfoText infoText = new InfoText();

   public Buffs() {
      super("Buffs", "Gives you lasting potion effects, each with its own switch, such as invulnerability, speed, haste and night vision, and can remove harmful ones.", Category.PLAYER);
      this.buff("God Mode", "Resistance V: no damage from mobs, falls, fire or explosions (the void still kills)", "resistance", 4, false);
      this.buff("Fire Resistance", "Take no damage from fire and lava", "fire_resistance", 0, false);
      this.buff("Speed", "Move faster", "speed", 1, true);
      this.buff("Haste", "Mine and attack faster", "haste", 1, true);
      this.buff("Strength", "Hit harder", "strength", 1, true);
      this.buff("Jump Boost", "Jump higher", "jump_boost", 1, true);
      this.buff("Night Vision", "See in the dark", "night_vision", 0, false);
      this.buff("Water Breathing", "Breathe under water", "water_breathing", 0, false);
      this.buff("Slow Falling", "Fall slowly", "slow_falling", 0, false);
      this.buff("Saturation", "Keep your food bar full", "saturation", 0, false);
      this.buff("Regeneration", "Heal over time", "regeneration", 1, true);
      this.buff("Dolphin's Grace", "Swim fast", "dolphins_grace", 0, false);
      this.buff("Hero of the Village", "Get discounts from villagers", "hero_of_the_village", 0, false);
      this.addSetting(new ActionSetting("Clear Bad Effects", "Remove every harmful effect you have right now", () -> this.cleanse(true)));
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Cleanse");
      names.add("Potion Spoof");
      names.add("PotionSpoof");
      names.add("NoHunger");
      names.add("No Hunger");
      names.add("God Mode");
      return names;
   }

   private void buff(String name, String description, String effectId, int defaultLevel, boolean leveled) {
      BooleanSetting toggle = this.addSetting(new BooleanSetting(name, description, false));
      NumberSetting level = leveled ? this.addSetting(new NumberSetting(name + " Level", "Strength of " + name, defaultLevel + 1, 1.0, 10.0, 1.0).showIf(toggle::get)) : null;
      Buffs.Buff buff = new Buffs.Buff(toggle, level, effectId, defaultLevel);
      toggle.onChange(() -> {
         if (this.isEnabled() && !this.applying) {
            if (toggle.get()) {
               buff.give(this.hideParticles.get());
            } else {
               buff.clear();
            }
         }
      });
      if (level != null) {
         level.onChange(() -> {
            if (this.isEnabled() && !this.applying && toggle.get()) {
               buff.clear();
               buff.give(this.hideParticles.get());
            }
         });
      }

      this.buffs.add(buff);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.refresh = 0;
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      }
   }

   /** A new world (or a respawn into one) starts without the effects: give them again right away. */
   @Override
   public void onWorldChange() {
      this.refresh = 0;
   }

   @Override
   public void onDisable() {
      if (Minecraft.getInstance().player != null) {
         for (Buffs.Buff buff : this.buffs) {
            if (buff.on.get()) {
               buff.clear();
            }
         }
      }
   }

   @Override
   public void onTick() {
      if (this.autoCleanse.get() && --this.cleanseWait <= 0) {
         this.cleanseWait = 10;
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && hasHarmfulEffect(player)) {
            this.cleanse(false);
         }
      }

      if (--this.refresh <= 0) {
         this.refresh = REFRESH_TICKS;
         this.applying = true;

         try {
            for (Buffs.Buff buff : this.buffs) {
               if (buff.on.get()) {
                  buff.give(this.hideParticles.get());
               }
            }
         } finally {
            this.applying = false;
         }
      }
   }

   /**
    * Removes the harmful effects the player has, one /effect clear each so the good ones stay.
    *
    * @param report whether to say in chat what was removed (the button does, the automatic sweep does not)
    */
   private void cleanse(boolean report) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      if (!SingleplayerGate.allowed()) {
         if (report) {
            CommandManager.feedback(SingleplayerGate.denialMessage());
         }

         return;
      }

      int removed = 0;
      // Copied first: the commands below change the player's effect map while it would still be read.
      for (MobEffectInstance instance : new ArrayList<>(player.getActiveEffects())) {
         MobEffect effect = instance.getEffect().value();
         Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
         if (effect.getCategory() == MobEffectCategory.HARMFUL && id != null && SpCommands.run("effect clear @s " + id)) {
            removed++;
         }
      }

      if (report) {
         CommandManager.feedback(removed == 0 ? "§bBuffs §7found no harmful effects." : "§bBuffs §7removed §f" + removed + " §7harmful effect" + (removed == 1 ? "" : "s") + ".");
      }
   }

   private static boolean hasHarmfulEffect(LocalPlayer player) {
      for (MobEffectInstance instance : player.getActiveEffects()) {
         if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            return true;
         }
      }

      return false;
   }

   @Override
   public String info() {
      int active = 0;

      for (Buffs.Buff buff : this.buffs) {
         if (buff.on.get()) {
            active++;
         }
      }

      return active == 0 ? null : this.infoText.whole(active, " active");
   }

   private record Buff(BooleanSetting on, NumberSetting level, String id, int defaultLevel) {
      int amplifier() {
         return this.level == null ? this.defaultLevel : Math.max(0, this.level.intValue() - 1);
      }

      void give(boolean hideParticles) {
         SpCommands.run("effect give @s minecraft:" + this.id + " infinite " + this.amplifier() + " " + hideParticles);
      }

      void clear() {
         SpCommands.run("effect clear @s minecraft:" + this.id);
      }
   }
}
