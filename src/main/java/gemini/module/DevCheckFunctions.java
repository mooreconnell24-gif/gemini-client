package gemini.module;

import static gemini.module.DevCheck.expect;
import static gemini.module.DevCheck.info;
import static gemini.module.DevCheck.module;
import static gemini.module.DevCheck.step;

import gemini.module.modules.misc.ClickGuiModule;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Functional checks: each module is switched on alone in a small arena built in the sky, the game runs for a few
 * ticks with simulated key presses, and one observable effect of the module is asserted.
 */
final class DevCheckFunctions {
   /** The arena floor is at {@code FLOOR}; the player stands on {@code FLOOR + 1}. */
   private static final int FLOOR = 199;
   private static int ax;
   private static int az;
   /** Values a check stores at its start and compares at its end. */
   private static final double[] mark = new double[4];
   private static final List<Module> paused = new ArrayList<>();
   /** When set, only the check for this module runs. */
   private static String only;

   private DevCheckFunctions() {
   }

   private static Minecraft mc() {
      return Minecraft.getInstance();
   }

   private static LocalPlayer player() {
      return Minecraft.getInstance().player;
   }

   private static void cmd(String format, Object... args) {
      SpCommands.run(String.format(Locale.ROOT, format, args));
   }

   private static void keys(boolean forward, boolean jump, boolean sneak) {
      mc().options.keyUp.setDown(forward);
      mc().options.keyJump.setDown(jump);
      mc().options.keyShift.setDown(sneak);
   }

   /** Clears and rebuilds the arena, puts the player in its middle facing south (+Z) in the given game mode. */
   private static void arena(String gamemode) {
      keys(false, false, false);
      cmd("fill %d %d %d %d %d %d minecraft:stone", ax - 12, FLOOR, az - 12, ax + 12, FLOOR, az + 12);
      cmd("fill %d %d %d %d %d %d minecraft:air", ax - 12, FLOOR + 1, az - 12, ax + 12, FLOOR + 12, az + 12);
      cmd("kill @e[type=!player,distance=..40]");
      cmd("clear @s");
      cmd("effect clear @s");
      cmd("gamemode %s", gamemode);
      cmd("tp @s %d.5 %d %d.5 0 0", ax, FLOOR + 1, az);
      cmd("effect give @s minecraft:instant_health 1 10 true");
      cmd("effect give @s minecraft:saturation 1 10 true");
   }

   /**
    * One check: build the arena, run {@code setup} (commands, items), wait, switch the module on, run for
    * {@code ticks} with {@code during} each step, then assert and switch it off.
    */
   private static void check(ModuleManager modules, String name, String gamemode, Runnable setup, int ticks, Runnable start, Supplier<String> problem) {
      Module module = only == null || only.equalsIgnoreCase(name.replace(" ", "")) || only.equalsIgnoreCase(name) ? module(modules, name) : null;
      if (module == null) {
         return;
      }

      step(8, () -> arena(gamemode));
      step(8, setup);
      step(ticks, () -> {
         if (start != null) {
            start.run();
         }

         module.setEnabled(true, false);
         if (!module.isEnabled()) {
            DevCheck.fail(name + ": refused to start (" + module.blockedReason().replaceAll("§.", "") + ")");
         }
      });
      step(2, () -> {
         expect(name, problem);
         keys(false, false, false);
         module.setEnabled(false, false);
      });
   }

   private static void none() {
   }

   private static double horizontal() {
      return Math.hypot(player().getX() - mark[0], player().getZ() - mark[2]);
   }

   private static void markPosition() {
      mark[0] = player().getX();
      mark[1] = player().getY();
      mark[2] = player().getZ();
   }

   private static int count(net.minecraft.world.item.Item item) {
      Inventory inventory = player().getInventory();
      int total = 0;
      for (int i = 0; i < inventory.getContainerSize(); i++) {
         if (inventory.getItem(i).is(item)) {
            total += inventory.getItem(i).getCount();
         }
      }

      return total;
   }

   static void queue(ModuleManager modules, String onlyModule) {
      only = onlyModule;
      step(1, () -> {
         ax = player().getBlockX();
         az = player().getBlockZ();
         paused.clear();
         for (Module module : modules.getModules()) {
            if (module.isEnabled() && !(module instanceof ClickGuiModule) && module.getCategory() != Category.HUD) {
               paused.add(module);
               module.setHeld(false);
            }
         }

         info("paused " + paused.size() + " running module(s) for the functional checks");
      });

      // Baseline: walking 20 ticks without any module, for the speed comparison.
      step(8, () -> arena("survival"));
      step(20, () -> {
         markPosition();
         keys(true, false, false);
      });
      step(2, () -> {
         mark[3] = horizontal();
         keys(false, false, false);
         info(String.format(Locale.ROOT, "baseline walk over 20 ticks: %.2f blocks", mark[3]));
      });

      List<Runnable> all = new ArrayList<>();
      all.add(() -> check(modules, "Speed", "survival", DevCheckFunctions::none, 20, () -> {
         double base = mark[3];
         markPosition();
         mark[3] = base;
         keys(true, false, false);
      }, () -> horizontal() > mark[3] * 1.15 ? null : String.format(Locale.ROOT, "walked %.2f vs %.2f without", horizontal(), mark[3])));

      all.add(() -> check(modules, "Flight", "survival", DevCheckFunctions::none, 15, () -> {
         markPosition();
         keys(false, true, false);
      }, () -> player().getY() - mark[1] > 2.0 ? null : String.format(Locale.ROOT, "rose %.2f blocks holding jump", player().getY() - mark[1])));

      all.add(() -> check(modules, "Step", "survival", () -> cmd("fill %d %d %d %d %d %d minecraft:stone", ax - 2, FLOOR + 1, az + 2, ax + 2, FLOOR + 1, az + 12), 25, () -> {
         markPosition();
         keys(true, false, false);
      }, () -> player().getY() - mark[1] > 0.9 ? null : String.format(Locale.ROOT, "Y changed %.2f walking into a 1-block step (step height %.2f, z moved %.2f)", player().getY() - mark[1], player().maxUpStep(), player().getZ() - mark[2])));

      all.add(() -> check(modules, "Spider", "survival", () -> cmd("fill %d %d %d %d %d %d minecraft:stone", ax - 2, FLOOR + 1, az + 1, ax + 2, FLOOR + 10, az + 1), 25, () -> {
         markPosition();
         keys(true, false, false);
      }, () -> player().getY() - mark[1] > 2.0 ? null : String.format(Locale.ROOT, "climbed %.2f blocks into a wall", player().getY() - mark[1])));

      all.add(() -> check(modules, "HighJump", "survival", DevCheckFunctions::none, 12, () -> {
         markPosition();
         mark[3] = 0;
         keys(false, true, false);
         peakTracker();
      }, () -> mark[3] > 1.5 ? null : String.format(Locale.ROOT, "jump peak %.2f blocks", mark[3])));

      all.add(() -> check(modules, "NoFall", "survival", () -> cmd("tp @s ~ ~10 ~"), 50, () -> {
         mark[1] = player().getHealth();
      }, () -> player().getHealth() >= mark[1] ? null : "took fall damage: " + mark[1] + " -> " + player().getHealth()));

      all.add(() -> check(modules, "Jesus", "survival", () -> {
         cmd("fill %d %d %d %d %d %d minecraft:water", ax - 3, FLOOR - 2, az - 3, ax + 3, FLOOR, az + 3);
         cmd("tp @s %d.5 %d.2 %d.5", ax, FLOOR + 1, az);
      }, 30, DevCheckFunctions::none, () -> player().getY() >= FLOOR + 0.7 ? null : String.format(Locale.ROOT, "sank to y=%.2f (surface %d)", player().getY(), FLOOR + 1)));

      all.add(() -> check(modules, "Glide", "survival", () -> cmd("tp @s ~ ~8 ~"), 6, DevCheckFunctions::none, () -> player().getDeltaMovement().y > -0.2 ? null : String.format(Locale.ROOT, "falling at %.2f blocks/tick", player().getDeltaMovement().y)));

      all.add(() -> check(modules, "AirJump", "survival", () -> cmd("tp @s ~ ~6 ~"), 3, () -> gemini.util.Scheduler.later(1, () -> keys(false, true, false)), () -> player().getDeltaMovement().y > 0.0 ? null : String.format(Locale.ROOT, "vertical speed %.2f holding jump in the air", player().getDeltaMovement().y)));

      all.add(() -> check(modules, "SafeWalk", "survival", () -> cmd("fill %d %d %d %d %d %d minecraft:air", ax - 12, FLOOR, az + 2, ax + 12, FLOOR, az + 12), 25, () -> {
         markPosition();
         keys(true, false, false);
      }, () -> player().getY() >= mark[1] - 0.01 ? null : "walked off the edge"));

      all.add(() -> check(modules, "AutoWalk", "survival", DevCheckFunctions::none, 20, DevCheckFunctions::markPosition, () -> horizontal() > 1.5 ? null : String.format(Locale.ROOT, "moved %.2f blocks", horizontal())));

      all.add(() -> check(modules, "Sprint", "survival", DevCheckFunctions::none, 15, () -> keys(true, false, false), () -> player().isSprinting() ? null : "not sprinting while walking forward"));

      all.add(() -> check(modules, "Timer", "survival", DevCheckFunctions::none, 5, DevCheckFunctions::none, () -> {
         float rate = mc().level.tickRateManager().tickrate();
         return Math.abs(rate - 20.0F) > 1.0F ? null : "tick rate is " + rate;
      }));

      all.add(() -> check(modules, "Tick Freeze", "survival", DevCheckFunctions::none, 5, DevCheckFunctions::none, () -> mc().level.tickRateManager().isFrozen() ? null : "the game is not frozen"));

      all.add(() -> check(modules, "Reach", "survival", DevCheckFunctions::none, 5, DevCheckFunctions::none, () -> player().blockInteractionRange() > 5.0 ? null : "block reach " + player().blockInteractionRange()));

      all.add(() -> check(modules, "Fullbright", "survival", DevCheckFunctions::none, 5, DevCheckFunctions::none,
         () -> mc().options.gamma().get() > 1.0 || player().hasEffect(MobEffects.NIGHT_VISION) ? null : "gamma " + mc().options.gamma().get() + " and no night vision"));

      all.add(() -> check(modules, "FOV", "survival", DevCheckFunctions::none, 5, DevCheckFunctions::none, () -> {
         float fov = mc().gameRenderer.mainCamera().getFov();
         return Math.abs(fov - 100.0F) < 2.0F ? null : "camera fov " + fov;
      }));

      all.add(() -> check(modules, "Freecam", "survival", DevCheckFunctions::none, 15, () -> {
         markPosition();
         keys(true, false, false);
      }, () -> {
         double camera = Math.hypot(mc().gameRenderer.mainCamera().position().x - player().getX(), mc().gameRenderer.mainCamera().position().z - player().getZ());
         return camera > 1.0 && horizontal() < 0.2 ? null : String.format(Locale.ROOT, "camera is %.2f from the player, player moved %.2f", camera, horizontal());
      }));

      all.add(() -> check(modules, "AutoTotem", "survival", () -> cmd("item replace entity @s hotbar.5 with minecraft:totem_of_undying"), 20, DevCheckFunctions::none,
         () -> player().getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? null : "offhand holds " + player().getOffhandItem()));

      all.add(() -> check(modules, "AutoArmor", "survival", () -> cmd("item replace entity @s inventory.3 with minecraft:diamond_chestplate"), 30, DevCheckFunctions::none,
         () -> player().getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE) ? null : "chest slot holds " + player().getItemBySlot(EquipmentSlot.CHEST)));

      all.add(() -> check(modules, "Item Magnet", "survival", () -> cmd("summon minecraft:item %d.5 %d %d.5 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:0}", ax + 5, FLOOR + 1, az + 5), 60, DevCheckFunctions::none,
         () -> count(Items.DIAMOND) > 0 ? null : "the diamond 7 blocks away was not collected"));

      all.add(() -> check(modules, "AutoEat", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:cooked_beef 8");
         cmd("effect clear @s");
         cmd("effect give @s minecraft:hunger 10 255 true");
      }, 200, DevCheckFunctions::none, () -> count(Items.COOKED_BEEF) < 8 ? null : "did not eat (food " + player().getFoodData().getFoodLevel() + ")"));

      all.add(() -> check(modules, "KillAura", "survival", () -> {
         cmd("difficulty easy");
         cmd("item replace entity @s hotbar.0 with minecraft:diamond_sword");
         cmd("summon minecraft:zombie %d.5 %d %d.5 {NoAI:1b,Health:20f}", ax, FLOOR + 1, az + 2);
      }, 40, DevCheckFunctions::none, () -> {
         List<LivingEntity> zombies = mc().level.getEntities(EntityTypes.ZOMBIE, new AABB(player().blockPosition()).inflate(6.0), e -> true).stream().map(z -> (LivingEntity)z).toList();
         return zombies.isEmpty() || zombies.get(0).getHealth() < 20.0F ? null : "the zombie 2 blocks away was not hit";
      }));

      all.add(() -> check(modules, "Nuker", "creative", () -> cmd("fill %d %d %d %d %d %d minecraft:dirt", ax + 1, FLOOR + 1, az + 1, ax + 2, FLOOR + 2, az + 2), 30, DevCheckFunctions::none,
         () -> mc().level.getBlockState(new BlockPos(ax + 1, FLOOR + 1, az + 1)).isAir() ? null : "dirt next to the player is still there"));

      all.add(() -> check(modules, "Scaffold", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:stone 64");
         cmd("fill %d %d %d %d %d %d minecraft:air", ax - 12, FLOOR, az + 1, ax + 12, FLOOR, az + 12);
      }, 30, () -> {
         markPosition();
         keys(true, false, false);
      }, () -> player().getY() >= mark[1] - 0.5 && player().getZ() - mark[2] > 1.5 ? null
         : String.format(Locale.ROOT, "walked %.2f out and dropped %.2f", player().getZ() - mark[2], mark[1] - player().getY())));

      all.add(() -> check(modules, "AutoTool", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:stick");
         cmd("item replace entity @s hotbar.4 with minecraft:diamond_pickaxe");
         cmd("setblock %d %d %d minecraft:stone", ax, FLOOR + 1, az + 1);
      }, 15, () -> {
         player().getInventory().setSelectedSlot(0);
         player().setXRot(58.0F);
         mc().options.keyAttack.setDown(true);
      }, () -> {
         mc().options.keyAttack.setDown(false);
         return player().getInventory().getSelectedSlot() == 4 ? null : "selected slot " + player().getInventory().getSelectedSlot() + " looking at " + mc().hitResult;
      }));

      all.add(() -> check(modules, "AutoRespawn", "survival", DevCheckFunctions::none, 40, () -> cmd("kill @s"),
         () -> player().isAlive() && !(mc().gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen) ? null : "still dead"));

      all.add(() -> check(modules, "Surround", "survival", () -> cmd("item replace entity @s hotbar.0 with minecraft:obsidian 16"), 20, DevCheckFunctions::none, () -> {
         int placed = 0;
         for (Direction side : Direction.Plane.HORIZONTAL) {
            if (mc().level.getBlockState(new BlockPos(ax, FLOOR + 1, az).relative(side)).is(Blocks.OBSIDIAN)) {
               placed++;
            }
         }

         return placed == 4 ? null : placed + " of 4 sides have obsidian";
      }));

      all.add(() -> check(modules, "AutoReplenish", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:cobblestone 2");
         cmd("item replace entity @s inventory.5 with minecraft:cobblestone 64");
      }, 30, DevCheckFunctions::none, () -> player().getInventory().getItem(0).getCount() > 2 ? null : "hotbar stack still " + player().getInventory().getItem(0).getCount()));

      all.add(() -> check(modules, "Zoom", "survival", DevCheckFunctions::none, 20, DevCheckFunctions::none, () -> {
         float fov = mc().gameRenderer.mainCamera().getFov();
         return fov < mc().options.fov().get() * 0.6F ? null : "camera fov " + fov + " (option " + mc().options.fov().get() + ")";
      }));

      all.add(() -> check(modules, "TriggerBot", "survival", () -> {
         cmd("difficulty easy");
         cmd("item replace entity @s hotbar.0 with minecraft:diamond_sword");
         cmd("summon minecraft:zombie %d.5 %d %d.5 {NoAI:1b,Health:20f}", ax, FLOOR + 1, az + 2);
      }, 40, () -> player().setXRot(20.0F), () -> {
         List<LivingEntity> zombies = mc().level.getEntities(EntityTypes.ZOMBIE, new AABB(player().blockPosition()).inflate(6.0), e -> true).stream().map(z -> (LivingEntity)z).toList();
         return zombies.isEmpty() || zombies.get(0).getHealth() < 20.0F ? null : "the zombie under the crosshair was not hit (looking at " + mc().hitResult + ")";
      }));

      all.add(() -> check(modules, "Auto Farm", "survival", () -> {
         cmd("setblock %d %d %d minecraft:farmland", ax + 1, FLOOR, az + 1);
         cmd("setblock %d %d %d minecraft:wheat[age=7]", ax + 1, FLOOR + 1, az + 1);
         cmd("item replace entity @s hotbar.1 with minecraft:wheat_seeds 4");
      }, 40, DevCheckFunctions::none, () -> count(Items.WHEAT) > 0 || mc().level.getBlockState(new BlockPos(ax + 1, FLOOR + 1, az + 1)).getBlock() != Blocks.WHEAT
         || !mc().level.getBlockState(new BlockPos(ax + 1, FLOOR + 1, az + 1)).getValue(net.minecraft.world.level.block.CropBlock.AGE).equals(7) ? null : "ripe wheat next to you was not harvested"));

      all.add(() -> check(modules, "Auto Shearer", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:shears");
         cmd("summon minecraft:sheep %d.5 %d %d.5 {NoAI:1b}", ax + 2, FLOOR + 1, az);
      }, 40, DevCheckFunctions::none, () -> {
         List<net.minecraft.world.entity.animal.sheep.Sheep> sheep = mc().level.getEntities(EntityTypes.SHEEP, new AABB(player().blockPosition()).inflate(6.0), e -> true);
         return sheep.isEmpty() ? "no sheep" : sheep.get(0).isSheared() ? null : "the sheep was not sheared";
      }));

      all.add(() -> check(modules, "Extinguish", "survival", () -> cmd("setblock %d %d %d minecraft:fire", ax + 2, FLOOR + 1, az + 1), 30, DevCheckFunctions::none,
         () -> mc().level.getBlockState(new BlockPos(ax + 2, FLOOR + 1, az + 1)).is(Blocks.FIRE) ? "the fire is still burning" : null));

      all.add(() -> check(modules, "Tower", "survival", () -> cmd("item replace entity @s hotbar.0 with minecraft:stone 64"), 30, () -> {
         markPosition();
         keys(false, true, false);
      }, () -> player().getY() - mark[1] > 2.0 && mc().level.getBlockState(new BlockPos(ax, FLOOR + 1, az)).is(Blocks.STONE) ? null
         : String.format(Locale.ROOT, "rose %.2f blocks, block under the start %s", player().getY() - mark[1], mc().level.getBlockState(new BlockPos(ax, FLOOR + 1, az)))));

      all.add(() -> check(modules, "LawnMower", "survival", () -> {
         cmd("setblock %d %d %d minecraft:grass_block", ax + 1, FLOOR, az + 1);
         cmd("setblock %d %d %d minecraft:short_grass", ax + 1, FLOOR + 1, az + 1);
      }, 30, DevCheckFunctions::none, () -> mc().level.getBlockState(new BlockPos(ax + 1, FLOOR + 1, az + 1)).isAir() ? null : "the grass next to you is still there"));

      all.add(() -> check(modules, "Auto Breed", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:wheat 8");
         cmd("summon minecraft:cow %d.5 %d %d.5 {NoAI:1b}", ax + 2, FLOOR + 1, az);
         cmd("summon minecraft:cow %d.5 %d %d.5 {NoAI:1b}", ax - 2, FLOOR + 1, az);
      }, 40, DevCheckFunctions::none, () -> count(Items.WHEAT) < 8 ? null : "no wheat was fed to the cows"));

      all.add(() -> check(modules, "Parkour", "survival", () -> cmd("fill %d %d %d %d %d %d minecraft:air", ax - 12, FLOOR, az + 2, ax + 12, FLOOR, az + 12), 12, () -> {
         markPosition();
         mark[3] = 0;
         keys(true, false, false);
         peakTracker();
      }, () -> mark[3] > 0.5 ? null : "did not jump at the edge (peak " + mark[3] + ")"));

      all.add(() -> check(modules, "Auto Torch", "survival", () -> {
         cmd("item replace entity @s hotbar.0 with minecraft:torch 16");
         cmd("fill %d %d %d %d %d %d minecraft:stone", ax - 4, FLOOR + 4, az - 4, ax + 4, FLOOR + 4, az + 4);
         cmd("time set midnight");
      }, 30, DevCheckFunctions::none, () -> count(Items.TORCH) < 16 ? null : "no torch placed in the dark"));

      all.add(() -> check(modules, "Offhand", "survival", () -> cmd("item replace entity @s inventory.2 with minecraft:totem_of_undying"), 20, DevCheckFunctions::none,
         () -> player().getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? null : "offhand holds " + player().getOffhandItem()));

      for (Runnable add : all) {
         add.run();
      }

      step(5, () -> {
         keys(false, false, false);
         cmd("gamemode creative");
         cmd("tick unfreeze");
         cmd("tick rate 20");
         cmd("time set day");
         for (Module module : paused) {
            module.setHeld(true);
         }

         paused.clear();
      });
   }

   /** Tracks the highest point above the start for a few ticks (HighJump). */
   private static void peakTracker() {
      for (int i = 1; i <= 12; i++) {
         gemini.util.Scheduler.later(i, () -> {
            if (player() != null) {
               mark[3] = Math.max(mark[3], player().getY() - mark[1]);
               if (mark[3] > 0.1) {
                  keys(false, false, false);
               }
            }
         });
      }
   }
}
