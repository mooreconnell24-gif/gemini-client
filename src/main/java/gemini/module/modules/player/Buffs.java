package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class Buffs extends Module {
   private static final int REFRESH_TICKS = 200;
   private final List<Buffs.Buff> buffs = new ArrayList<>();
   private final BooleanSetting hideParticles = this.addSetting(new BooleanSetting("Hide Particles", "Give the effects without the swirling particles", true));
   private int refresh;
   private boolean applying;

   public Buffs() {
      super("Buffs", "Permanent potion effects on switches: god mode, speed, haste, night vision and more.", Category.PLAYER);
      this.buff("God Mode", "Resistance V: no damage from mobs, falls, fire or explosions (the void still kills)", "resistance", 4, false);
      this.buff("Fire Resistance", "Lava and fire do nothing", "fire_resistance", 0, false);
      this.buff("Speed", "Move faster", "speed", 1, true);
      this.buff("Haste", "Mine and attack faster", "haste", 1, true);
      this.buff("Strength", "Hit harder", "strength", 1, true);
      this.buff("Jump Boost", "Jump higher", "jump_boost", 1, true);
      this.buff("Night Vision", "See in the dark", "night_vision", 0, false);
      this.buff("Water Breathing", "Breathe under water", "water_breathing", 0, false);
      this.buff("Slow Falling", "Float down gently", "slow_falling", 0, false);
      this.buff("Saturation", "Never go hungry", "saturation", 0, false);
      this.buff("Regeneration", "Heal over time", "regeneration", 1, true);
      this.buff("Dolphin's Grace", "Swim fast", "dolphins_grace", 0, false);
      this.buff("Hero of the Village", "Villager discounts", "hero_of_the_village", 0, false);
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

   @Override
   public String info() {
      int active = 0;

      for (Buffs.Buff buff : this.buffs) {
         if (buff.on.get()) {
            active++;
         }
      }

      return active == 0 ? null : active + " active";
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
