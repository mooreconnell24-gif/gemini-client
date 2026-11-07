package com.inferno.scenario;

import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.checks.ClientBrands;
import com.inferno.fabric.checks.PacketCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class Scenarios {
   private Scenarios() {
   }

   private static void fill(ServerLevel w, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
      for (int x = x1; x <= x2; x++) {
         for (int y = y1; y <= y2; y++) {
            for (int z = z1; z <= z2; z++) {
               w.setBlock(new BlockPos(x, y, z), state, 3);
            }
         }
      }
   }

   /** Put the player (and the simulated client) somewhere else before the scenario starts. */
   private static void place(Scenario s, double x, double y, double z) {
      s.client.p.snapTo(x, y, z, 0.0F, 0.0F);
      s.client.x = x;
      s.client.y = y;
      s.client.z = z;
   }

   /** Mouse-like yaw: a whole number of 0.15° steps (sensitivity 50%). */
   private static float mouseYaw(double degrees) {
      return (float)(Math.round(degrees / 0.15) * 0.15);
   }

   private static Pig pig(Scenario s, double dz) {
      ServerLevel w = s.client.level;
      Pig pig = EntityTypes.PIG.create(w, EntitySpawnReason.COMMAND);
      pig.snapTo(s.bx + 0.5, s.groundY, s.bz + 0.5 + dz, 180.0F, 0.0F);
      pig.setNoAi(true);
      w.addFreshEntity(pig);
      s.vars.put("pig", pig);
      return pig;
   }

   static List<Scenario> all() {
      List<Scenario> l = new ArrayList<>();

      // ======================= legit play: must raise no flag at all =======================

      l.add(Scenario.legit("walk, sprint, sprint-jump, strafe, coast to a stop", 260, (s, t) -> {
         Client c = s.client;
         float yaw = mouseYaw(Math.sin(t / 15.0) * 20.0);
         if (t < 40) {
            c.walk(true, false, false, false, false, false, false, yaw);
         } else if (t < 120) {
            c.walk(true, false, false, false, false, true, false, yaw);
         } else if (t < 200) {
            c.walk(true, false, false, false, true, true, false, yaw);
         } else if (t < 230) {
            c.walk(true, true, false, false, false, false, false, yaw);
         } else {
            c.walk(false, false, false, false, false, false, false, yaw);
         }
      }));
      l.add(Scenario.legit("sneak-walking", 80, (s, t) -> s.client.walk(true, false, false, false, false, false, true, 0.0F)));
      l.add(Scenario.legit("stand still, then jump in place", 130, (s, t) -> {
         // Stop jumping 30 ticks before the end so the player has landed before packets stop.
         if (t >= 40) {
            s.client.walk(false, false, false, false, t < 100, false, false, 0.0F);
         }
      }));
      l.add(Scenario.legit("walk off a 3-block ledge", 70, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            fill(s.client.level, s.bx - 3, g, s.bz - 3, s.bx + 3, g + 2, s.bz + 3, Blocks.STONE.defaultBlockState());
            place(s, s.bx + 0.5, g + 3, s.bz + 0.5);
            int bx = s.bx;
            int bz = s.bz;
            s.client.ground = (x, z) -> x + 0.3 > bx - 3 && x - 0.3 < bx + 4 && z + 0.3 > bz - 3 && z - 0.3 < bz + 4 ? g + 3 : g;
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.walk(true, false, false, false, false, false, false, 0.0F);
         }
      }));
      l.add(Scenario.legit("switch slots, use an item, place blocks while looking at them", 120, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            s.client.p.getInventory().setItem(1, new ItemStack(Items.STICK));
         }

         @Override
         public void tick(Scenario s, int t) {
            Client c = s.client;
            BlockPos[] targets = {new BlockPos(s.bx, s.groundY - 1, s.bz + 2), new BlockPos(s.bx + 1, s.groundY - 1, s.bz + 2), new BlockPos(s.bx - 1, s.groundY - 1, s.bz + 2)};
            switch (t) {
               case 5 -> c.slot(1);
               case 15 -> c.useItem();
               case 25 -> c.slot(0);
               default -> {
               }
            }

            for (int i = 0; i < targets.length; i++) {
               if (t == 40 + i * 25) {
                  c.lookAt(Vec3.atCenterOf(targets[i]).add(0.0, 0.5, 0.0));
               } else if (t == 43 + i * 25) {
                  c.useOn(targets[i]);
                  c.swing();
               }
            }
         }
      }));
      l.add(Scenario.legit("mine a block while looking at it, swinging", 40, (s, t) -> {
         Client c = s.client;
         BlockPos pos = new BlockPos(s.bx, s.groundY - 1, s.bz + 2);
         if (t == 0) {
            c.lookAt(Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
         } else if (t == 2) {
            c.action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos);
            c.swing();
         } else if (t > 2 && t < 24) {
            c.swing();
            if (t == 23) {
               c.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos);
            }
         }
      }));
      l.add(Scenario.legit("walk, open the inventory (keys released), click, walk on", 100, (s, t) -> {
         Client c = s.client;
         if (t < 20) {
            c.walk(true, false, false, false, false, false, false, 0.0F);
         } else if (t < 70) {
            // Screen open: vanilla releases every key, so the client reports no input and stays put.
            c.walk(false, false, false, false, false, false, false, 0.0F);
            if (t == 30 || t == 37 || t == 48 || t == 54 || t == 67) {
               InfernoMod.ENGINE.clickSlot(c.p);
            }
         } else {
            c.walk(true, false, false, false, false, false, false, 0.0F);
         }
      }));

      l.add(Scenario.legit("sprint at a ledge, let go of W and coast off it", 60, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            fill(s.client.level, s.bx - 3, g, s.bz - 3, s.bx + 3, g + 2, s.bz + 3, Blocks.STONE.defaultBlockState());
            place(s, s.bx + 0.5, g + 3, s.bz - 2.5);
            int bx = s.bx;
            int bz = s.bz;
            s.client.ground = (x, z) -> x + 0.3 > bx - 3 && x - 0.3 < bx + 4 && z + 0.3 > bz - 3 && z - 0.3 < bz + 4 ? g + 3 : g;
         }

         @Override
         public void tick(Scenario s, int t) {
            boolean w = s.client.z < s.bz + 3.2;
            s.client.walk(w, false, false, false, false, w, false, 0.0F);
         }
      }));
      l.add(Scenario.legit("walk into a wall holding W (sudden stop, not SafeWalk)", 60, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            fill(s.client.level, s.bx - 3, g, s.bz + 3, s.bx + 3, g + 2, s.bz + 3, Blocks.STONE.defaultBlockState());
            // The player's front edge (z + 0.3) touches the wall at bz + 3.
            s.client.maxZ = s.bz + 3 - 0.3;
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.walk(true, false, false, false, false, false, false, 0.0F);
         }
      }));

      // ======================= cheats: the named check must flag =======================

      l.add(Scenario.cheat("Speed without pressing a key", "input-move", 25, (s, t) -> {
         Client c = s.client;
         c.move(c.x, c.y, c.z + 0.28, true);
      }));
      l.add(Scenario.cheat("Sprint while holding back (omni-sprint)", "sprint-invalid", 20, (s, t) -> {
         Client c = s.client;
         if (t == 0) {
            c.keys(false, false, true, false, false, false, true);
            c.command(ServerboundPlayerCommandPacket.Action.START_SPRINTING);
         }

         c.move(c.x, c.y, c.z - 0.28, true);
      }));
      l.add(Scenario.cheat("Sprint while starving (Sprint ignore hunger)", "sprint-invalid", 40, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getFoodData().setFoodLevel(4);
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.walk(true, false, false, false, false, true, false, 0.0F);
         }
      }));
      l.add(Scenario.cheat("Speed auto-jump (no jump key)", "jump-input", 90, (s, t) -> {
         s.client.hideJumpKey = true;
         s.client.walk(true, false, false, false, true, true, false, 0.0F);
      }));
      l.add(Scenario.cheat("HighJump (0.65 jump impulse)", "high-jump", 40, (s, t) -> {
         s.client.jumpPower = 0.65;
         s.client.walk(false, false, false, false, true, false, false, 0.0F);
      }));
      l.add(Scenario.cheat("ReverseStep off two ledges", "reverse-step", 80, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            fill(s.client.level, s.bx - 3, g, s.bz - 3, s.bx + 3, g + 5, s.bz + 3, Blocks.STONE.defaultBlockState());
            fill(s.client.level, s.bx - 3, g, s.bz + 4, s.bx + 3, g + 2, s.bz + 7, Blocks.STONE.defaultBlockState());
            place(s, s.bx + 0.5, g + 6, s.bz + 0.5);
            int bx = s.bx;
            int bz = s.bz;
            s.client.ground = (x, z) -> {
               boolean inX = x + 0.3 > bx - 3 && x - 0.3 < bx + 4;
               if (inX && z + 0.3 > bz - 3 && z - 0.3 < bz + 4) {
                  return g + 6;
               } else {
                  return inX && z + 0.3 > bz + 4 && z - 0.3 < bz + 8 ? g + 3 : g;
               }
            };
            s.client.snapDown = true;
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.walk(true, false, false, false, false, false, false, 0.0F);
         }
      }));
      l.add(Scenario.cheat("NoJumpDelay (re-jump every 2 ticks, jump held)", "jump-delay", 30, (s, t) -> {
         Client c = s.client;
         c.keys(false, false, false, false, true, false, false);
         if (t % 2 == 0) {
            c.move(c.x, s.groundY + 0.42, c.z, false);
         } else {
            c.move(c.x, s.groundY, c.z, true);
         }
      }));
      l.add(Scenario.cheat("Silent rotations: 3 rotation packets per tick", "multi-move", 10, (s, t) -> {
         Client c = s.client;

         for (int i = 0; i < 3; i++) {
            c.rot(mouseYaw(t * 3 + i * 40), 0.0F);
         }
      }));
      l.add(Scenario.cheat("Timer x2 (two client ticks per server tick)", "tick-timer", 120, (s, t) -> {
         Client c = s.client;
         c.manualTickEnd = true;
         c.tickEnd();
         c.tickEnd();
      }));
      l.add(Scenario.cheat("SuperKnockback (sprint toggled twice per tick)", "sprint-reset", 6, (s, t) -> {
         s.client.command(ServerboundPlayerCommandPacket.Action.START_SPRINTING);
         s.client.command(ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
      }));
      l.add(Scenario.cheat("SilentHotbar (swap, use, swap back in one tick)", "hotbar-swap", 50, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(3, new ItemStack(Items.STICK));
         }

         @Override
         public void tick(Scenario s, int t) {
            if (t % 10 == 0 && t > 0) {
               s.client.slot(3);
               s.client.useItem();
               s.client.slot(0);
            }
         }
      }));
      l.add(Scenario.cheat("Invalid hotbar slot", "hotbar-swap", 10, (s, t) -> {
         if (t == 5) {
            s.client.slot(12);
         }
      }));
      l.add(Scenario.cheat("InventoryMove (clicking with W held)", "inventory-input", 40, (s, t) -> {
         if (t == 0) {
            s.client.keys(true, false, false, false, false, false, false);
         }

         if (t == 10 || t == 20 || t == 30) {
            InfernoMod.ENGINE.clickSlot(s.client.p);
         }
      }));
      l.add(Scenario.cheat("ChestStealer with a fixed 100 ms delay (100 clicks)", "inventory-bot", 210, new Scenario.Script() {
         @Override
         public void early(Scenario s, int t) {
            // A bot on a decent connection: clicks leave on its schedule, not after other work.
            @SuppressWarnings("unchecked")
            List<Long> times = (List<Long>)s.vars.computeIfAbsent("times", k -> new ArrayList<Long>());
            if (t % 2 == 0 && t < 200) {
               times.add(System.currentTimeMillis());
               InfernoMod.ENGINE.clickSlot(s.client.p);
            }
         }

         @Override
         public void tick(Scenario s, int t) {
            if (t == 205) {
               @SuppressWarnings("unchecked")
               List<Long> times = (List<Long>)s.vars.get("times");
               ScenarioMod.LOG.info("[scenario] bot click schedule deviation as the server saw it: {} ms", com.inferno.fabric.checks.Detect.scheduleDeviation(times.subList(0, 16)));
            }
         }
      }));
      l.add(Scenario.cheat("MoreCarry (walking with items in the crafting grid)", "more-carry", 70, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.DIAMOND));
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.walk(true, false, false, false, false, false, false, 0.0F);
         }
      }));
      l.add(Scenario.cheat("AutoRespawn (respawn 200 ms after death)", "auto-respawn", 10, (s, t) -> {
         if (t == 0) {
            InfernoMod.ENGINE.death(s.client.p);
         } else if (t == 4) {
            s.client.net.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
         }
      }));
      l.add(Scenario.cheat("PacketMine (finish blocks never started)", "dig-packets", 20, (s, t) -> {
         if (t == 5 || t == 10 || t == 15) {
            s.client.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, new BlockPos(s.bx + 1, s.groundY - 1, s.bz + 1 + t));
         }
      }));
      l.add(Scenario.cheat("Nuker (breaking the block underfoot while looking up)", "break-aim", 40, (s, t) -> {
         if (t == 0) {
            s.client.rot(0.0F, -90.0F);
         }

         if (t == 5 || t == 15 || t == 25) {
            s.client.action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, new BlockPos(s.bx + (t / 10), s.groundY - 1, s.bz));
         }
      }));
      l.add(Scenario.cheat("Fucker (breaking a block behind a wall)", "break-aim", 30, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            s.client.level.setBlock(new BlockPos(s.bx, g, s.bz + 3), Blocks.STONE.defaultBlockState(), 3);
            fill(s.client.level, s.bx - 1, g, s.bz + 2, s.bx + 1, g + 2, s.bz + 2, Blocks.STONE.defaultBlockState());
         }

         @Override
         public void tick(Scenario s, int t) {
            BlockPos target = new BlockPos(s.bx, s.groundY, s.bz + 3);
            if (t == 0) {
               s.client.lookAt(Vec3.atCenterOf(target));
            }

            if (t == 5 || t == 15 || t == 25) {
               s.client.action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, target);
            }
         }
      }));
      l.add(Scenario.cheat("LiquidPlace (placing against water)", "liquid-place", 20, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            // Two separate pools: the first placement fills its water with stone.
            s.client.level.setBlock(new BlockPos(s.bx, s.groundY, s.bz + 2), Blocks.WATER.defaultBlockState(), 3);
            s.client.level.setBlock(new BlockPos(s.bx + 2, s.groundY, s.bz + 2), Blocks.WATER.defaultBlockState(), 3);
            s.client.p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
         }

         @Override
         public void tick(Scenario s, int t) {
            BlockPos water = new BlockPos(s.bx + (t >= 10 ? 2 : 0), s.groundY, s.bz + 2);
            if (t == 0 || t == 10) {
               s.client.lookAt(Vec3.atCenterOf(water));
            }

            if (t == 5 || t == 15) {
               s.client.useOn(water);
            }
         }
      }));
      l.add(Scenario.cheat("Surround (4 blocks placed in one tick)", "tick-interact", 30, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
         }

         @Override
         public void tick(Scenario s, int t) {
            if (t == 10 || t == 20) {
               int z = s.bz + (t == 10 ? 1 : -1);

               for (int dx = -2; dx <= 1; dx++) {
                  s.client.useOn(new BlockPos(s.bx + dx, s.groundY - 1, z));
               }
            }
         }
      }));
      l.add(Scenario.cheat("CrystalAura (crystals hit the tick they appear)", "crystal-aura", 20, (s, t) -> {
         if (t == 5 || t == 10 || t == 15) {
            ServerLevel w = s.client.level;
            EndCrystal crystal = new EndCrystal(w, s.bx + 0.5, s.groundY + 4, s.bz + 2.5);
            w.addFreshEntity(crystal);
            s.client.attack(crystal);
         }
      }));
      l.add(Scenario.cheat("VehicleOneHit / multi-hit (3 attacks in one tick)", "tick-attacks", 25, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            pig(s, 1.5);
         }

         @Override
         public void tick(Scenario s, int t) {
            Pig pig = (Pig)s.vars.get("pig");
            if (t == 0) {
               s.client.lookAt(pig.getEyePosition());
            }

            if (t == 10 || t == 20) {
               for (int i = 0; i < 3; i++) {
                  s.client.swing();
                  s.client.attack(pig);
               }
            }
         }
      }));
      l.add(Scenario.cheat("MaceKill (fake 3-block rise, then smash)", "mace-kill", 15, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            pig(s, 1.5);
            s.client.p.getInventory().setItem(0, new ItemStack(Items.MACE));
         }

         @Override
         public void tick(Scenario s, int t) {
            Client c = s.client;
            Pig pig = (Pig)s.vars.get("pig");
            if (t == 0) {
               c.lookAt(pig.getEyePosition());
            } else if (t == 5) {
               c.move(c.x, s.groundY + 3, c.z, false);
            } else if (t == 6) {
               c.move(c.x, s.groundY + 1, c.z, false);
               c.swing();
               c.attack(pig);
            }
         }
      }));
      l.add(Scenario.cheat("AntiLevitation (staying on the ground)", "anti-levitation", 80, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 600, 0));
         }

         @Override
         public void tick(Scenario s, int t) {
         }
      }));
      l.add(Scenario.cheat("Sneak at walking speed", "sneak-speed", 30, (s, t) -> {
         Client c = s.client;
         if (t == 0) {
            c.keys(true, false, false, false, false, true, false);
         }

         if (t >= 10) {
            c.move(c.x, c.y, c.z + 0.22, true);
         }
      }));
      l.add(Scenario.cheat("LiquidWalk swim speed (0.6 b/t in water)", "liquid-speed", 14, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            fill(s.client.level, s.bx - 2, s.groundY, s.bz - 1, s.bx + 2, s.groundY + 1, s.bz + 12, Blocks.WATER.defaultBlockState());
         }

         @Override
         public void tick(Scenario s, int t) {
            s.client.move(s.client.x, s.client.y, s.client.z + 0.6, false);
         }
      }));
      l.add(Scenario.cheat("ElytraFly (level flight without losing speed)", "elytra-fly", 35, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            place(s, s.bx + 0.5, s.groundY + 30, s.bz + 0.5);
            s.client.p.setOnGround(false);
            s.client.onGround = false;
         }

         @Override
         public void tick(Scenario s, int t) {
            Client c = s.client;
            if (t == 0) {
               c.command(ServerboundPlayerCommandPacket.Action.START_FALL_FLYING);
            } else {
               c.move(c.x, c.y, c.z + 1.0, false);
            }
         }
      }));
      l.add(Scenario.cheat("ServerCrasher packet flood (800 packets/s)", "packet-flood", 60, (s, t) -> {
         for (int i = 0; i < 40; i++) {
            PacketCounter.count(s.client.p.getUUID());
         }
      }));
      l.add(Scenario.cheat("LiquidBounce client brand", "client-brand", 5, (s, t) -> {
         if (t == 1) {
            ClientBrands.put(s.client.p.getUUID(), "LiquidBounce");
            InfernoMod.ENGINE.join(s.client.p);
         }
      }));
      l.add(Scenario.cheat("Spammer (same message 3 times)", "chat-spam", 50, (s, t) -> {
         if (t == 5 || t == 25 || t == 45) {
            InfernoMod.ENGINE.chat(s.client.p, "Buy cheap diamonds at our shop! " + t);
         }
      }));
      l.add(Scenario.cheat("BookBot (4 books in 2 s)", "book-spam", 40, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(0, new ItemStack(Items.WRITABLE_BOOK));
         }

         @Override
         public void tick(Scenario s, int t) {
            if (t % 10 == 5) {
               s.client.net.handleEditBook(new ServerboundEditBookPacket(0, List.of("page " + t), Optional.empty()));
            }
         }
      }));
      l.add(Scenario.cheat("FastBreak (stone at 83% of its break time)", "break-progress", 100, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));

            for (int dx = -1; dx <= 1; dx++) {
               s.client.level.setBlock(new BlockPos(s.bx + dx, s.groundY, s.bz + 2), Blocks.STONE.defaultBlockState(), 3);
            }
         }

         @Override
         public void tick(Scenario s, int t) {
            Client c = s.client;
            int k = t / 30;
            int phase = t % 30;
            if (k > 2) {
               return;
            }

            BlockPos pos = new BlockPos(s.bx - 1 + k, s.groundY, s.bz + 2);
            if (t == 0) {
               // Standing on the ground (mining in the air is 5x slower).
               c.move(c.x, c.y, c.z, true);
            }

            if (phase == 0) {
               c.lookAt(Vec3.atCenterOf(pos));
            } else if (phase == 2) {
               c.action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos);
               c.swing();
            } else if (phase > 2 && phase <= 21) {
               c.swing();
               if (phase == 21) {
                  c.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos);
               }
            }
         }
      }));
      l.add(Scenario.cheat("AutoBow (8 shots at exactly 20 ticks)", "auto-bow", 205, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            s.client.p.getInventory().setItem(0, new ItemStack(Items.BOW));
            s.client.p.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
         }

         @Override
         public void tick(Scenario s, int t) {
            int phase = t % 25;
            if (phase == 0) {
               s.client.useItem();
            } else if (phase == 20) {
               s.client.action(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO);
            }
         }
      }));
      l.add(Scenario.cheat("SafeWalk (stopping dead at a ledge, W held)", "safe-walk", 300, new Scenario.Script() {
         @Override
         public void setUp(Scenario s) {
            int g = s.groundY;
            fill(s.client.level, s.bx - 3, g, s.bz - 3, s.bx + 3, g + 2, s.bz + 3, Blocks.STONE.defaultBlockState());
            place(s, s.bx + 0.5, g + 3, s.bz + 0.5);
            int bx = s.bx;
            int bz = s.bz;
            s.client.ground = (x, z) -> x + 0.3 > bx - 3 && x - 0.3 < bx + 4 && z + 0.3 > bz - 3 && z - 0.3 < bz + 4 ? g + 3 : g;
            s.client.maxZ = bz + 4 + 0.29;
         }

         @Override
         public void tick(Scenario s, int t) {
            // 37-tick cycle: walk to the edge (and stay stopped there with W held), then back off.
            int phase = t % 37;
            if (phase < 25) {
               s.client.walk(true, false, false, false, false, false, false, 0.0F);
            } else {
               s.client.walk(false, false, true, false, false, false, false, 0.0F);
            }
         }
      }));
      return l;
   }
}
