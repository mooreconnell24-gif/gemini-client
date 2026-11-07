package com.inferno.fabric.checks;

import com.inferno.fabric.InfernoConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CombatChecks2 {
   private CombatChecks2() {
   }

   public static final class ClickConsistency extends Check {
      public ClickConsistency() {
         super("autoclicker-consistency", "AutoClicker (Consistency)", Check.Category.COMBAT, "Clicks too evenly spaced to be human.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         CombatChecks2.ClickConsistency.Clicks c = this.data(s, CombatChecks2.ClickConsistency.Clicks::new);
         long now = System.currentTimeMillis();
         long gap = now - c.last;
         c.last = now;
         if (gap <= 250L && gap >= 20L) {
            c.gaps.add(gap);
            if (c.gaps.size() < 25) {
               return false;
            }

            double mean = 0.0;

            for (long g : c.gaps) {
               mean += g;
            }

            mean /= c.gaps.size();
            double var = 0.0;

            for (long g : c.gaps) {
               var += (g - mean) * (g - mean);
            }

            double deviation = Math.sqrt(var / (c.gaps.size() - 1));
            c.gaps.clear();
            if (deviation < 10.0 && mean <= 125.0) {
               if (this.buffer(s, 1.0) >= 2.0) {
                  this.flag(p, Math.round(1000.0 / mean) + " CPS with only " + round(deviation) + "ms variation");
               }
            } else {
               this.decay(s, 0.5);
            }

            return false;
         } else {
            c.gaps.clear();
            return false;
         }
      }

      private static final class Clicks {
         long last;
         final List<Long> gaps = new ArrayList<>();
      }
   }

   public static final class KillAuraWalls extends Check {
      public KillAuraWalls() {
         super("killaura-walls", "KillAura (Walls)", Check.Category.COMBAT, "Hitting targets through solid walls.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (target instanceof LivingEntity && !p.hasLineOfSight(target)) {
            if (this.buffer(s, 1.0) >= 3.0) {
               this.flag(p, "hit " + target.getName().getString() + " through a wall");
               return true;
            } else {
               return false;
            }
         } else {
            this.decay(s, 0.5);
            return false;
         }
      }
   }

   public static final class NoSwing extends Check {
      public NoSwing() {
         super("noswing", "NoSwing", Check.Category.COMBAT, "Hitting without swinging the arm.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         s.unswungAttacks.addLast(System.currentTimeMillis());

         while (s.unswungAttacks.size() > 20) {
            s.unswungAttacks.removeFirst();
         }

         return false;
      }

      @Override
      public void onTick(ServerPlayer p, PlayerState s) {
         long now = System.currentTimeMillis();

         while (!s.unswungAttacks.isEmpty() && now - s.unswungAttacks.peekFirst() > 250L) {
            long attack = s.unswungAttacks.removeFirst();
            if (Math.abs(s.lastSwing - attack) > 250L) {
               if (this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -3.0);
                  this.flag(p, "hit without swinging");
               }
            } else {
               this.decay(s, 0.5);
            }
         }
      }
   }

   public static final class SpeedAverage extends Check {
      public SpeedAverage() {
         super("speed-average", "Speed (Average)", Check.Category.MOVEMENT, "Going faster on average than sprint-jumping allows (BHop, Strafe).");
      }

      @Override
      public void onMove(ServerPlayer p, PlayerState s, Move m) {
         Deque<Double> moves = this.data(s, ArrayDeque::new);
         if (m.moved) {
            if (m.checkable() && !m.ice && !m.soul && !m.liquid && !m.headBlocked && !m.sticky) {
               moves.addLast(m.horizontal);

               while (moves.size() > 20) {
                  moves.removeFirst();
               }

               if (moves.size() >= 20) {
                  double sum = 0.0;

                  for (double d : moves) {
                     sum += d;
                  }

                  double avg = sum / moves.size();
                  double limit;
                  if (avg > (limit = InfernoConfig.get().limit("speed-average.max-average"))) {
                     if (this.buffer(s, 1.0) >= 4.0) {
                        this.buffer(s, -3.0);
                        moves.clear();
                        this.flag(p, "average " + round(avg) + " b/t (limit " + round(limit) + ")");
                        m.setback();
                     }
                  } else {
                     this.decay(s, 0.25);
                  }
               }
            } else {
               moves.clear();
            }
         }
      }
   }
}
