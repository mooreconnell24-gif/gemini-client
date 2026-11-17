package gemini.module;

import static gemini.module.DevCheck.action;
import static gemini.module.DevCheck.expect;
import static gemini.module.DevCheck.module;
import static gemini.module.DevCheck.setBool;
import static gemini.module.DevCheck.setMode;
import static gemini.module.DevCheck.setNumber;
import static gemini.module.DevCheck.step;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.Setting;
import gemini.util.SpCommands;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/** The modules that drive the integrated server with commands, each run once with its effect checked. */
final class DevCheckCommands {
   private DevCheckCommands() {
   }

   private static LocalPlayer player() {
      return Minecraft.getInstance().player;
   }

   private static ClientLevel level() {
      return Minecraft.getInstance().level;
   }

   private static MinecraftServer server() {
      return Minecraft.getInstance().getSingleplayerServer();
   }

   static void queue(ModuleManager modules) {
      step(2, () -> {
         SpCommands.clearFailures();
         SpCommands.run("gamemode creative");
         SpCommands.run("clear @s");
         SpCommands.run("effect clear @s");
      });

      Module buffs = module(modules, "Buffs");
      step(10, () -> {
         for (Setting<?> setting : buffs.settings()) {
            if (setting instanceof BooleanSetting bool && !bool.name().equals("Hide Particles") && !bool.name().equals("God Mode")) {
               bool.set(true);
            }
         }

         buffs.setEnabled(true, false);
      });
      step(10, () -> {
         expect("Buffs gives effects", () -> player().hasEffect(MobEffects.SPEED) && player().hasEffect(MobEffects.NIGHT_VISION) ? null : "no speed/night vision after enabling");
         buffs.setEnabled(false, false);
      });
      step(2, () -> expect("Buffs clears effects on disable", () -> player().hasEffect(MobEffects.SPEED) ? "speed still active" : null));
      step(1, () -> buffs.settings().forEach(Setting::reset));

      Module rules = module(modules, "World Tools");
      step(10, () -> {
         setMode(rules, "Time", "Midnight");
         action(rules, "Set Time");
      });
      step(2, () -> expect("World Tools sets midnight", () -> {
         long day = server().overworld().getOverworldClockTime() % 24000L;
         return Math.abs(day - 18000L) < 200L ? null : "time of day is " + day;
      }));
      step(20, () -> {
         setMode(rules, "Weather", "Rain");
         action(rules, "Set Weather");
      });
      // The client eases the rain in over about a second, so give it two.
      step(40, () -> {
      });
      step(2, () -> {
         expect("World Tools sets rain", () -> level().isRaining() ? null : "not raining");
         setMode(rules, "Weather", "Clear");
         action(rules, "Set Weather");
         setMode(rules, "Time", "Day");
         action(rules, "Set Time");
      });

      step(5, () -> {
         setBool(rules, "Keep Inventory", true);
         setBool(rules, "Restore On Disable", true);
         setNumber(rules, "Random Tick Speed", 10);
         rules.setEnabled(true, false);
      });
      step(20, () -> {
         expect("World Tools sets keep_inventory", () -> server().overworld().getGameRules().get(GameRules.KEEP_INVENTORY) ? null : "keep_inventory still false");
         expect("World Tools sets random_tick_speed", () -> {
            int speed = server().overworld().getGameRules().get(GameRules.RANDOM_TICK_SPEED);
            return speed == 10 ? null : "random_tick_speed is " + speed;
         });
         rules.setEnabled(false, false);
      });
      step(20, () -> {
         expect("World Tools restores on disable", () -> server().overworld().getGameRules().get(GameRules.KEEP_INVENTORY) ? "keep_inventory still true" : null);
         rules.settings().forEach(Setting::reset);
      });

      Module kits = module(modules, "Kits");
      for (String kit : List.of("PvP", "Crystal", "Builder", "Miner", "Explorer", "Food")) {
         step(10, () -> {
            SpCommands.run("clear @s");
            setMode(kits, "Kit", kit);
            kits.setEnabled(true, false);
         });
         step(0, () -> expect("Kits " + kit, () -> player().getInventory().isEmpty() ? "inventory empty" : null));
      }

      Module enchanter = module(modules, "Enchanter");
      step(10, () -> {
         SpCommands.run("clear @s");
         SpCommands.run("item replace entity @s weapon.mainhand with minecraft:diamond_sword");
         SpCommands.run("item replace entity @s armor.head with minecraft:diamond_helmet");
         SpCommands.run("item replace entity @s armor.feet with minecraft:diamond_boots");
      });
      step(10, () -> enchanter.setEnabled(true, false));
      step(10, () -> {
         expect("Enchanter held item", () -> player().getMainHandItem().isEnchanted() ? null : "sword not enchanted");
         action(enchanter, "Enchant Armour");
      });
      step(10, () -> {
         expect("Enchanter armour", () -> player().getItemBySlot(EquipmentSlot.HEAD).isEnchanted() && player().getItemBySlot(EquipmentSlot.FEET).isEnchanted() ? null : "armour not enchanted");
         expect("Enchanter keeps the sword", () -> player().getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD) ? null : "main hand now holds " + player().getMainHandItem());
         expect("Enchanter empties the scratch slot", () -> player().getOffhandItem().isEmpty() ? null : "offhand holds " + player().getOffhandItem());
         action(enchanter, "+30 Levels");
      });
      step(2, () -> expect("Enchanter +30 levels", () -> player().experienceLevel >= 30 ? null : "level " + player().experienceLevel));

      Module platform = module(modules, "Platform");
      step(10, () -> SpCommands.run("execute positioned over motion_blocking run tp @s ~ ~ ~"));
      step(10, () -> action(platform, "Place"));
      BlockPos[] placed = new BlockPos[1];
      step(10, () -> {
         BlockPos below = player().blockPosition().below();
         placed[0] = below;
         expect("Platform place", () -> level().getBlockState(below).is(Blocks.GLASS) ? null : "block below is " + level().getBlockState(below));
         action(platform, "Remove");
      });
      step(10, () -> {
         BlockPos below = placed[0];
         expect("Platform remove", () -> level().getBlockState(below).isAir() ? null : "block below is " + level().getBlockState(below));
      });

      Module summoner = module(modules, "Summoner");
      Module sweeper = module(modules, "Mob Sweeper");
      step(10, () -> {
         SpCommands.run("execute positioned over motion_blocking run tp @s ~ ~ ~");
         setMode(summoner, "Mob", "Cow");
         setNumber(summoner, "Count", 3);
      });
      step(20, () -> summoner.setEnabled(true, false));
      step(5, () -> {
         expect("Summoner", () -> cows() >= 3 ? null : cows() + " cows nearby");
         setMode(sweeper, "Targets", "All Mobs");
         sweeper.setEnabled(true, false);
      });
      step(60, () -> {
      });
      step(2, () -> {
         expect("Mob Sweeper", () -> cows() == 0 ? null : cows() + " cows left");
         sweeper.setEnabled(false, false);
         sweeper.settings().forEach(Setting::reset);
      });

      step(5, () -> SpCommands.run("effect give @s minecraft:poison 60 0"));
      step(5, () -> action(buffs, "Clear Bad Effects"));
      step(5, () -> expect("Buffs clears bad effects", () -> player().hasEffect(MobEffects.POISON) ? "still poisoned" : null));
      step(5, () -> {
         setBool(buffs, "Auto Cleanse", true);
         buffs.setEnabled(true, false);
         SpCommands.run("effect give @s minecraft:wither 60 0");
      });
      step(25, () -> {
      });
      step(2, () -> {
         expect("Buffs auto cleanse", () -> player().hasEffect(MobEffects.WITHER) ? "still withered" : null);
         buffs.setEnabled(false, false);
         setBool(buffs, "Auto Cleanse", false);
      });

      step(10, () -> {
         setMode(rules, "Game Mode", "Survival");
         action(rules, "Set Game Mode");
      });
      step(2, () -> expect("World Tools sets game mode", () -> Minecraft.getInstance().gameMode.getPlayerMode() == GameType.SURVIVAL ? null : "still " + Minecraft.getInstance().gameMode.getPlayerMode()));

      Module heal = module(modules, "Regen");
      step(5, () -> SpCommands.run("damage @s 10 minecraft:generic"));
      step(5, () -> {
         setNumber(heal, "Amount", 2);
         setNumber(heal, "Interval", 5);
         setNumber(heal, "Below Health", 16);
         heal.setEnabled(true, false);
      });
      step(30, () -> {
      });
      step(2, () -> {
         expect("Regen heals below the threshold", () -> player().getHealth() > 12.0F ? null : "health " + player().getHealth());
         heal.setEnabled(false, false);
         heal.settings().forEach(Setting::reset);
         SpCommands.run("gamemode creative");
      });

      step(5, () -> {
         SpCommands.run("clear @s");
         setMode(kits, "Kit", "Custom");
         action(kits, "Give");
      });
      step(3, () -> {
         expect("Kits Custom", () -> player().getInventory().isEmpty() ? "inventory empty" : null);
         setMode(kits, "Kit", "PvP");
         SpCommands.run("clear @s");
      });

      Module home = module(modules, "Home");
      double[] at = new double[3];
      step(5, () -> {
         action(home, "Set Home");
         at[0] = player().getX();
         at[2] = player().getZ();
         SpCommands.run("tp @s ~30 ~ ~30");
      });
      step(10, () -> action(home, "Go Home"));
      step(10, () -> expect("Home go", () -> Math.abs(player().getX() - at[0]) < 2 && Math.abs(player().getZ() - at[2]) < 2 ? null : "not back home"));

      Module save = module(modules, "Auto Save");
      step(20, () -> action(save, "Save Now"));
      step(2, () -> expect("Auto Save", () -> SpCommands.recentFailures().stream().anyMatch(f -> f.contains("save")) ? "saving failed" : null));
   }

   private static int cows() {
      LocalPlayer player = player();
      return level().getEntities(EntityTypes.COW, new AABB(player.blockPosition()).inflate(24.0), cow -> true).size();
   }
}
