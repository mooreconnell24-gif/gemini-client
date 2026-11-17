package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Game rules on switches, sent with their 26.3 ids (snake_case; the old camelCase names such as {@code keepInventory}
 * are rejected by the 26.3 command parser). {@code SpCommandsSyntaxTest} checks every id against the real dispatcher.
 *
 * <p>Game rules are saved with the world: they stay as set after this module is switched off unless "Restore On
 * Disable" is on, and leaving the world keeps them too.
 */
public final class WorldRules extends Module {
   private final List<WorldRules.Rule> rules = new ArrayList<>();
   private final NumberSetting tickSpeed;
   private final ModeSetting difficulty;
   private final BooleanSetting restore = this.addSetting(
      new BooleanSetting("Restore On Disable", "Put every rule and the random tick speed back to their vanilla defaults when you turn this off (difficulty is left as it is)", false)
   );
   private boolean applying;

   public WorldRules() {
      super("World Rules", "Changes game rules such as Keep Inventory and Mob Griefing without typing commands; they stay set after turning off unless Restore On Disable is on.", Category.WORLD);
      this.rule("Keep Inventory", "Keep your items and XP when you die", false, true, "keep_inventory");
      this.rule("Daylight Cycle", "Let the sun move; off freezes the time of day", true, true, "advance_time");
      this.rule("Weather Cycle", "Let the weather change by itself", true, true, "advance_weather");
      this.rule("Mob Spawning", "Let monsters and animals spawn naturally", true, true, "spawn_mobs");
      this.rule("Mob Griefing", "Creepers, endermen and others can change blocks", true, true, "mob_griefing");
      // 26.x replaced the doFireTick switch with a radius (default 128 blocks around a player, 0 = fire never spreads).
      this.valuedRule("Fire Spread", "Fire spreads and burns blocks", true, true, "128", "0", "fire_spread_radius_around_player");
      this.rule("Phantoms", "Phantoms spawn when you skip sleeping", true, true, "spawn_phantoms");
      this.rule("Fall Damage", "Take damage from falling", true, true, "fall_damage");
      this.rule("Instant Respawn", "Respawn without the death screen", false, false, "immediate_respawn");
      this.tickSpeed = this.addSetting(
         new NumberSetting("Random Tick Speed", "How fast crops, saplings and grass grow (vanilla is 3)", 3.0, 0.0, 300.0, 1.0)
            .onChange(() -> this.sendTickSpeed(false))
      );
      this.difficulty = this.addSetting(
         new ModeSetting("Difficulty", "World difficulty; Keep leaves it alone", "Keep", "Keep", "Peaceful", "Easy", "Normal", "Hard")
            .onChange(() -> this.sendDifficulty(false))
      );
   }

   private void sendDifficulty(boolean force) {
      if (!this.difficulty.is("Keep") && (force || this.isEnabled() && !this.applying)) {
         SpCommands.run("difficulty " + this.difficulty.get().toLowerCase(Locale.ROOT));
      }
   }

   private void rule(String name, String description, boolean ruleDefault, boolean vanilla, String... ids) {
      this.valuedRule(name, description, ruleDefault, vanilla, "true", "false", ids);
   }

   /** A switch whose game rule takes {@code onValue} when the switch is on and {@code offValue} when it is off. */
   private void valuedRule(String name, String description, boolean ruleDefault, boolean vanilla, String onValue, String offValue, String... ids) {
      WorldRules.Rule rule = new WorldRules.Rule(
         this.addSetting(new BooleanSetting(name, description, ruleDefault)), vanilla, onValue, offValue, ids
      );
      rule.setting.onChange(() -> {
         if (this.isEnabled() && !this.applying) {
            rule.send(rule.setting.get());
         }
      });
      this.rules.add(rule);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      // Only rules you changed from vanilla are sent, so switching this on never undoes rules you set yourself.
      this.applying = true;
      try {
         for (WorldRules.Rule rule : this.rules) {
            if (rule.setting.get() != rule.vanilla) {
               rule.send(rule.setting.get());
            }
         }

         if (this.tickSpeed.intValue() != 3) {
            this.sendTickSpeed(true);
         }

         this.sendDifficulty(true);
      } finally {
         this.applying = false;
      }
   }

   @Override
   public void onDisable() {
      if (this.restore.get() && Minecraft.getInstance().player != null) {
         for (WorldRules.Rule rule : this.rules) {
            rule.send(rule.vanilla);
         }

         send("3", "random_tick_speed");
      }
   }

   private void sendTickSpeed(boolean force) {
      if (force || this.isEnabled() && !this.applying) {
         send(Integer.toString(this.tickSpeed.intValue()), "random_tick_speed");
      }
   }

   private static void send(String value, String... ids) {
      for (String id : ids) {
         SpCommands.run("gamerule " + id + " " + value);
      }
   }

   @Override
   public String info() {
      int changed = 0;
      for (WorldRules.Rule rule : this.rules) {
         if (rule.setting.get() != rule.vanilla) {
            changed++;
         }
      }

      return changed == 0 ? null : changed + " changed";
   }

   private record Rule(BooleanSetting setting, boolean vanilla, String onValue, String offValue, String[] ids) {
      void send(boolean value) {
         WorldRules.send(value ? this.onValue : this.offValue, this.ids);
      }
   }
}
