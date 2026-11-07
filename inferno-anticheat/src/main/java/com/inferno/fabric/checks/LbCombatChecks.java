package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;

/** Combat checks added for LiquidBounce coverage. */
public final class LbCombatChecks {
   private LbCombatChecks() {
   }

   public static final class CrystalAura extends Check {
      public CrystalAura() {
         super("crystal-aura", "CrystalAura", Check.Category.COMBAT, "Breaking end crystals the instant they appear, or faster than anyone can click (CrystalAura).");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (!(target instanceof EndCrystal crystal)) {
            return false;
         }

         Data d = this.data(s, Data::new);
         long now = System.currentTimeMillis();
         d.hits.addLast(now);

         while (!d.hits.isEmpty() && now - d.hits.peekFirst() > 1000L) {
            d.hits.removeFirst();
         }

         if (d.hits.size() >= 10) {
            d.hits.clear();
            this.flag(p, "broke 10 end crystals in a second");
            return InfernoConfig.get().cancelHits;
         }

         // A crystal that has existed for at most one server tick can't have been seen yet: it takes a
         // round trip plus a reaction to hit it.
         if (crystal.tickCount <= 1 && !InfernoMod.ENGINE.serverLagging()) {
            if (this.buffer(s, 1.0) >= 3.0) {
               this.buffer(s, -1.0);
               this.flag(p, "broke an end crystal " + crystal.tickCount * 50 + " ms after it appeared");
               return InfernoConfig.get().cancelHits;
            }
         } else {
            this.decay(s, 0.25);
         }

         return false;
      }

      private static final class Data {
         final Deque<Long> hits = new ArrayDeque<>();
      }
   }

   public static final class MaceKill extends Check {
      public MaceKill() {
         super("mace-kill", "MaceKill", Check.Category.COMBAT, "Mace smash attacks after a fake upward teleport that pumps up fall distance (MaceKill).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         // Nothing in vanilla moves a player up more than a block in one packet except server-side pushes
         // (wind charges, explosions, teleports), which recentlyPushed() covers.
         if (m.dy > 1.0 && !s.recentlyPushed(1500L)) {
            this.data(s, Data::new).lastRise = m.time;
         }
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (p.getMainHandItem().is(Items.MACE) && p.fallDistance >= 1.5) {
            long since = System.currentTimeMillis() - this.data(s, Data::new).lastRise;
            if (since < 1500L) {
               this.flag(p, "mace smash with " + round(p.fallDistance) + " fall distance " + since + " ms after jumping up over a block in one packet");
               return InfernoConfig.get().cancelHits;
            }
         }

         return false;
      }

      private static final class Data {
         long lastRise;
      }
   }

   public static final class AutoBow extends Check {
      public AutoBow() {
         super("auto-bow", "AutoBow", Check.Category.COMBAT, "Releasing the bow at exactly the same draw time shot after shot (AutoBow, AutoShoot).");
      }

      @Override
      public void onAction(ServerPlayer p, PlayerState s, ServerboundPlayerActionPacket.Action action, BlockPos pos) {
         if (action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM && p.isUsingItem() && p.getUseItem().is(Items.BOW)) {
            int charge = p.getTicksUsingItem();
            if (charge >= 3) {
               Data d = this.data(s, Data::new);
               d.charges.addLast(charge);

               while (d.charges.size() > 8) {
                  d.charges.removeFirst();
               }

               if (d.charges.size() == 8) {
                  int min = Integer.MAX_VALUE;
                  int max = Integer.MIN_VALUE;

                  for (int c : d.charges) {
                     min = Math.min(min, c);
                     max = Math.max(max, c);
                  }

                  if (min == max) {
                     d.charges.clear();
                     this.flag(p, "8 shots in a row released after exactly " + charge + " ticks");
                  }
               }
            }
         }
      }

      private static final class Data {
         final Deque<Integer> charges = new ArrayDeque<>();
      }
   }

   public static final class TickAttacks extends Check {
      public TickAttacks() {
         super("tick-attacks", "Attacks (Per Tick)", Check.Category.COMBAT, "Several attack packets inside one client tick (VehicleOneHit, MultiActions, KillAura multi-hit).");
      }

      @Override
      public void onClientTick(ServerPlayer p, PlayerState s, ClientTick tick) {
         if (tick.attacks > 0 && System.currentTimeMillis() - tick.start < 150L && !InfernoMod.ENGINE.serverLagging()) {
            int same = tick.maxAttacksOnOneTarget();
            if (same >= 3 || tick.attacks >= 4) {
               if (this.buffer(s, 1.0) >= 1.5) {
                  this.buffer(s, -1.0);
                  this.flag(p, tick.attacks + " attacks in one tick (" + same + " on one target)");
               }
            } else {
               this.decay(s, 0.05);
            }
         }
      }
   }

   public static final class SprintReset extends Check {
      public SprintReset() {
         super("sprint-reset", "SuperKnockback", Check.Category.COMBAT, "Toggling sprint more than once inside one client tick to reset knockback (SuperKnockback, KeepSprint packet modes).");
      }

      @Override
      public void onClientTick(ServerPlayer p, PlayerState s, ClientTick tick) {
         // LocalPlayer#sendIsSprintingIfNeeded runs once per tick, so a vanilla client sends at most one.
         if (tick.sprintCommands >= 2 && System.currentTimeMillis() - tick.start < 150L) {
            if (this.buffer(s, 1.0) >= 2.0) {
               this.buffer(s, -1.0);
               this.flag(p, tick.sprintCommands + " sprint toggles in one tick" + (tick.attacks > 0 ? " around an attack" : ""));
            }
         } else if (tick.sprintCommands == 1) {
            this.decay(s, 0.02);
         }
      }
   }
}
