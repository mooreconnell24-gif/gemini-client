package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Game rules on switches. Rule ids changed from camelCase to snake_case in recent versions, so each rule is sent
 * under every known spelling; the ones the running version does not know fail silently.
 */
public final class WorldRules extends Module {
   private final List<WorldRules.Rule> rules = new ArrayList<>();
   private final NumberSetting tickSpeed;
   private final BooleanSetting restore = this.addSetting(
      new BooleanSetting("Restore On Disable", "Put every rule back to its vanilla default when you switch this off", false)
   );
   private boolean applying;

   public WorldRules() {
      super("World Rules", "Flip game rules like Keep Inventory and Mob Griefing without typing commands.", Category.WORLD);
      this.rule("Keep Inventory", "Keep your items and XP when you die", false, true, "keepInventory", "keep_inventory");
      this.rule("Daylight Cycle", "Let the sun move; off freezes the time of day", true, true, "doDaylightCycle", "advance_time", "do_daylight_cycle");
      this.rule("Weather Cycle", "Let the weather change by itself", true, true, "doWeatherCycle", "advance_weather", "do_weather_cycle");
      this.rule("Mob Spawning", "Let monsters and animals spawn naturally", true, true, "doMobSpawning", "spawn_mobs", "do_mob_spawning");
      this.rule("Mob Griefing", "Creepers, endermen and others can change blocks", true, true, "mobGriefing", "mob_griefing");
      this.rule("Fire Spread", "Fire spreads and burns blocks", true, true, "doFireTick", "fire_tick", "do_fire_tick");
      this.rule("Phantoms", "Phantoms spawn when you skip sleeping", true, true, "doInsomnia", "spawn_phantoms", "do_insomnia");
      this.rule("Fall Damage", "Take damage from falling", true, true, "fallDamage", "fall_damage");
      this.rule("Instant Respawn", "Respawn without the death screen", false, false, "doImmediateRespawn", "immediate_respawn", "do_immediate_respawn");
      this.tickSpeed = this.addSetting(
         new NumberSetting("Random Tick Speed", "How fast crops, saplings and grass grow (vanilla is 3)", 3.0, 0.0, 300.0, 1.0)
            .onChange(() -> this.sendTickSpeed(false))
      );
   }

   private void rule(String name, String description, boolean ruleDefault, boolean vanilla, String... ids) {
      WorldRules.Rule rule = new WorldRules.Rule(
         this.addSetting(new BooleanSetting(name, description, ruleDefault)), vanilla, ids
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

         send("3", "randomTickSpeed", "random_tick_speed");
      }
   }

   private void sendTickSpeed(boolean force) {
      if (force || this.isEnabled() && !this.applying) {
         send(Integer.toString(this.tickSpeed.intValue()), "randomTickSpeed", "random_tick_speed");
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

   private record Rule(BooleanSetting setting, boolean vanilla, String[] ids) {
      void send(boolean value) {
         WorldRules.send(Boolean.toString(value), this.ids);
      }
   }
}
