package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * The world's settings in one place. The top of the list is one-shot buttons (time of day, weather, game mode,
 * seed) that work whether or not the module is on; the rest are game rules that are applied while it is on.
 *
 * <p>Game rules on switches, sent with their 26.3 ids (snake_case; the old camelCase names such as {@code keepInventory}
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
      super(
         "World Tools",
         "Sets the time, weather and game mode with one click and changes game rules such as Keep Inventory and Mob Griefing while it is on.",
         Category.WORLD
      );
      ModeSetting time = this.addSetting(new ModeSetting("Time", "Time of day the Set Time button sets", "Day", "Day", "Noon", "Night", "Midnight", "Custom"));
      NumberSetting timeTicks = this.addSetting(
         new NumberSetting("Time Ticks", "Time of day in ticks for Custom (0 = sunrise, 6000 = noon, 18000 = midnight)", 6000.0, 0.0, 24000.0, 250.0)
            .showIf(() -> time.is("Custom"))
      );
      this.addSetting(new ActionSetting("Set Time", "Set the world to the chosen time of day now; switch Daylight Cycle off to keep it there", () -> {
         String value = time.is("Custom") ? Integer.toString(timeTicks.intValue()) : time.get().toLowerCase(Locale.ROOT);
         report(SpCommands.run("time set " + value), "The time is now §f" + time.get());
      }));
      ModeSetting weather = this.addSetting(new ModeSetting("Weather", "Weather the Set Weather button sets", "Clear", "Clear", "Rain", "Thunder"));
      this.addSetting(new ActionSetting("Set Weather", "Set the chosen weather now; switch Weather Cycle off to keep it", () -> {
         report(SpCommands.run("weather " + weather.get().toLowerCase(Locale.ROOT)), "The weather is now §f" + weather.get());
      }));
      ModeSetting gameMode = this.addSetting(
         new ModeSetting("Game Mode", "Game mode the Set Game Mode button switches you to", "Creative", "Survival", "Creative", "Adventure", "Spectator")
      );
      this.addSetting(new ActionSetting("Set Game Mode", "Switch to the chosen game mode now", () -> {
         report(SpCommands.gamemode(gameMode.get()), "Your game mode is now §f" + gameMode.get());
      }));
      this.addSetting(new ActionSetting("Show Seed", "Write the world seed in chat", () -> {
         MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
         report(server != null, server == null ? "" : "World seed: §f" + server.overworld().getSeed());
      }));
      this.rule("Keep Inventory", "Keep your items and XP when you die", false, false, "keep_inventory");
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

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("World Rules");
      names.add("Time");
      names.add("Weather");
      names.add("Gamemode");
      names.add("Game Mode");
      names.add("Seed");
      names.add("Game Rules");
      return names;
   }

   private static void report(boolean worked, String message) {
      CommandManager.feedback(worked ? "§7" + message : "§cWorld Tools §7needs a single-player world.");
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
         // Only the rules this module changed: the others already hold their vanilla value.
         for (WorldRules.Rule rule : this.rules) {
            if (rule.setting.get() != rule.vanilla) {
               rule.send(rule.vanilla);
            }
         }

         if (this.tickSpeed.intValue() != 3) {
            send("3", "random_tick_speed");
         }
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
