package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;

/** Player / inventory checks added for LiquidBounce coverage. */
public final class LbPlayerChecks {
   private LbPlayerChecks() {
   }

   static boolean survival(ServerPlayer p) {
      return !p.isCreative() && !p.isSpectator();
   }

   /** Movement or jump keys. Sneak and sprint are left out because they can be toggles. */
   static boolean walkingKeys(Input in) {
      return in.forward() || in.backward() || in.left() || in.right() || in.jump();
   }

   static String keys(Input in) {
      StringBuilder b = new StringBuilder();
      if (in.forward()) {
         b.append('W');
      }

      if (in.left()) {
         b.append('A');
      }

      if (in.backward()) {
         b.append('S');
      }

      if (in.right()) {
         b.append('D');
      }

      if (in.jump()) {
         b.append(b.isEmpty() ? "jump" : "+jump");
      }

      return b.toString();
   }

   public static final class HotbarSwap extends Check {
      public HotbarSwap() {
         super("hotbar-swap", "Hotbar (Silent Swap)", Check.Category.PLAYER, "Switching slot, acting and switching back inside one tick, or picking a slot that doesn't exist (SilentHotbar, AutoWeapon, AutoTool, AutoRod, AutoBuff, Scaffold auto-block).");
      }

      @Override
      public void onCarriedSlot(ServerPlayer p, PlayerState s, int from, int to) {
         Data d = this.data(s, Data::new);
         if (to < 0 || to > 8) {
            this.flag(p, "selected hotbar slot " + to + " (only 0-8 exist)");
            return;
         }

         long now = System.currentTimeMillis();
         if (d.acted && d.changeTick == s.clientTicks && to == d.prevSlot) {
            d.swaps.addLast(now);

            while (!d.swaps.isEmpty() && now - d.swaps.peekFirst() > 30000L) {
               d.swaps.removeFirst();
            }

            if (d.swaps.size() >= 3) {
               d.swaps.clear();
               this.flag(p, "swapped to slot " + (from + 1) + ", used it and swapped back to " + (to + 1) + " inside one tick, 3 times in 30 s");
            }
         }

         d.prevSlot = from;
         d.changeTick = s.clientTicks;
         d.acted = false;
      }

      private void acted(PlayerState s) {
         Data d = this.data(s, Data::new);
         if (d.changeTick == s.clientTicks) {
            d.acted = true;
         }
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         this.acted(s);
         return false;
      }

      @Override
      public void onUseItem(ServerPlayer p, PlayerState s, InteractionHand hand) {
         this.acted(s);
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         this.acted(s);
         return false;
      }

      @Override
      public void onAction(ServerPlayer p, PlayerState s, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
         if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK || action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            this.acted(s);
         }
      }

      private static final class Data {
         int prevSlot = -1;
         long changeTick = -10L;
         boolean acted;
         final Deque<Long> swaps = new ArrayDeque<>();
      }
   }

   public static final class InventoryInput extends Check {
      public InventoryInput() {
         super("inventory-input", "InvMove (Keys)", Check.Category.PLAYER, "Holding movement keys while a chest or the inventory is open; vanilla releases every key when a screen opens (InventoryMove, AutoArmor, ChestStealer, InventoryCleaner).");
      }

      @Override
      public void onClickSlot(ServerPlayer p, PlayerState s) {
         Input in = p.getLastClientInput();
         // Container clicks only come from an open screen, and opening a screen releases all keys
         // (Gui#setScreen -> KeyMapping.releaseAll), so held keys here mean the keys were never released.
         if (survival(p) && walkingKeys(in) && System.currentTimeMillis() - s.lastInputChange > 150L && !p.isPassenger()) {
            if (this.buffer(s, 1.0) >= 2.0) {
               this.buffer(s, -1.0);
               this.flag(p, "clicked in an inventory while holding " + keys(in));
            }
         } else {
            this.decay(s, 0.25);
         }
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         long now = System.currentTimeMillis();
         Input in = p.getLastClientInput();
         if (survival(p)
            && s.containerOpen
            && now - s.containerOpenTime > 400L + Math.min(p.connection.latency(), 1000)
            && walkingKeys(in)
            && now - s.lastInputChange > 150L
            && now - d.lastFlag > 3000L) {
            d.lastFlag = now;
            if (this.buffer(s, 1.0) >= 2.0) {
               this.buffer(s, -1.0);
               this.flag(p, "holding " + keys(in) + " with a container open");
            }
         }
      }

      private static final class Data {
         long lastFlag;
      }
   }

   public static final class InventoryBot extends Check {
      public InventoryBot() {
         super("inventory-bot", "Inventory (Bot Timing)", Check.Category.PLAYER, "Inventory clicks on a fixed machine schedule, twice in a row (ChestStealer, InventoryCleaner, AutoArmor, AutoDeposit, Replenish, AutoCrafter with a fixed delay).");
      }

      @Override
      public void onClickSlot(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         long now = System.currentTimeMillis();
         long gap = d.times.isEmpty() ? 0L : now - d.times.get(d.times.size() - 1);
         if (!d.times.isEmpty() && (gap < 30L || gap > 400L)) {
            // Bursts (drag-clicking) and pauses aren't part of a steady rhythm: start a new run.
            d.times.clear();
         }

         d.times.add(now);
         if (d.times.size() >= 16) {
            double deviation = Detect.scheduleDeviation(d.times);
            double mean = (d.times.get(d.times.size() - 1) - d.times.get(0)) / (double)(d.times.size() - 1);
            d.times.clear();
            double max = InfernoConfig.get().limit("inventory-bot.max-deviation");
            // One steady window can happen to a careful person; two in a row (32 clicks) practically can't.
            if (deviation < max && mean >= 40.0) {
               if (++d.lowWindows >= 2) {
                  d.lowWindows = 0;
                  this.flag(p, "32 inventory clicks on a " + Math.round(mean) + " ms schedule, off by only " + round(deviation) + " ms");
               }
            } else {
               d.lowWindows = 0;
            }
         }
      }

      private static final class Data {
         final List<Long> times = new ArrayList<>();
         int lowWindows;
      }
   }

   public static final class MoreCarry extends Check {
      public MoreCarry() {
         super("more-carry", "MoreCarry", Check.Category.PLAYER, "Walking around with items kept in the 2x2 crafting grid; vanilla empties it when the inventory closes (MoreCarry, InventoryMove).");
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         Data d = this.data(s, Data::new);
         long now = System.currentTimeMillis();
         if (survival(p)
            && !s.containerOpen
            && !p.inventoryMenu.getCraftSlots().isEmpty()
            && walkingKeys(p.getLastClientInput())
            && now - s.lastClick > 2000L
            && now - s.lastInputChange > 150L
            && now - s.lastPositionChange < 100L
            && s.lastHorizontal > 0.05) {
            if (++d.ticks >= 40) {
               d.ticks = 0;
               this.flag(p, "walking for 2 s with items in the crafting grid");
            }
         } else {
            d.ticks = 0;
         }
      }

      private static final class Data {
         int ticks;
      }
   }

   public static final class AutoRespawn extends Check {
      public AutoRespawn() {
         super("auto-respawn", "AutoRespawn", Check.Category.PLAYER, "Respawning before the death screen's button turns on (it stays off for 1 s) (AutoRespawn).");
      }

      @Override
      public void onRespawnRequest(ServerPlayer p, PlayerState s) {
         if (s.deathTime != 0L && !Boolean.TRUE.equals(p.level().getGameRules().get(GameRules.IMMEDIATE_RESPAWN))) {
            long took = System.currentTimeMillis() - s.deathTime;
            s.deathTime = 0L;
            double min = InfernoConfig.get().limit("auto-respawn.min-ms");
            if (took < min) {
               this.flag(p, "respawned " + took + " ms after dying");
            }
         }
      }
   }
}
