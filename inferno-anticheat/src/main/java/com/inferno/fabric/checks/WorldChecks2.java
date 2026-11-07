package com.inferno.fabric.checks;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public final class WorldChecks2 {
   private WorldChecks2() {
   }

   public static final class AirPlace extends Check {
      public AirPlace() {
         super("airplace", "AirPlace", Check.Category.WORLD, "Placing blocks against nothing.");
      }

      @Override
      public boolean onPlace(ServerPlayer p, PlayerState s, BlockHitResult hit) {
         if (!p.level().getBlockState(hit.getBlockPos()).isAir()) {
            this.decay(s, 0.5);
            return false;
         } else if (this.buffer(s, 1.0) >= 2.0) {
            this.flag(p, "placed a block against air");
            return true;
         } else {
            return false;
         }
      }
   }

   public static final class Criticals extends Check {
      public Criticals() {
         super("criticals", "Criticals", Check.Category.COMBAT, "Fake critical hits while standing on the ground.");
      }

      @Override
      public boolean onAttack(ServerPlayer p, PlayerState s, Entity target) {
         if (!p.onGround() && !(p.fallDistance <= 0.0) && !CheckEngine.special(p) && !p.isInWater() && !p.onClimbable()) {
            ServerLevel w = p.level();
            AABB box = p.getBoundingBox();
            double frac = p.getY() - Math.floor(p.getY());
            boolean restingOnBlock = (frac < 0.001 || Math.abs(frac - 0.5) < 0.001) && !w.noCollision(p, box.move(0.0, -0.002, 0.0));
            if (restingOnBlock) {
               if (this.buffer(s, 1.0) >= 2.0) {
                  this.flag(p, "critical hit while standing on a block");
                  return true;
               }
            } else {
               this.decay(s, 0.25);
            }

            return false;
         } else {
            return false;
         }
      }
   }

   public static final class XRay extends Check {
      private static final long WINDOW = 1200000L;

      public XRay() {
         super("xray", "XRay (Ratio)", Check.Category.WORLD, "Finding diamonds/ancient debris much faster than normal mining (alerts only).");
      }

      @Override
      public boolean onBreak(ServerPlayer p, PlayerState s, BlockPos pos) {
         String name = CheckEngine.blockId(p.level().getBlockState(pos));
         WorldChecks2.XRay.Mining m = this.data(s, WorldChecks2.XRay.Mining::new);
         long now = System.currentTimeMillis();

         while (!m.veins.isEmpty() && now - ((long[])m.veins.peekFirst())[0] > 1200000L) {
            m.veins.removeFirst();
         }

         while (!m.stone.isEmpty() && now - m.stone.peekFirst() > 1200000L) {
            m.stone.removeFirst();
         }

         if (!name.contains("diamond_ore") && !name.contains("ancient_debris")) {
            if (name.contains("stone")
               || name.contains("deepslate")
               || name.contains("netherrack")
               || name.contains("tuff")
               || name.contains("andesite")
               || name.contains("diorite")
               || name.contains("granite")
               || name.contains("basalt")) {
               m.stone.addLast(now);

               while (m.stone.size() > 5000) {
                  m.stone.removeFirst();
               }
            }
         } else {
            for (long[] v : m.veins) {
               if (Math.abs(v[1] - pos.getX()) <= 3L && Math.abs(v[2] - pos.getY()) <= 3L && Math.abs(v[3] - pos.getZ()) <= 3L) {
                  return false;
               }
            }

            m.veins.addLast(new long[]{now, pos.getX(), pos.getY(), pos.getZ()});
            double per100 = m.veins.size() * 100.0 / Math.max(1, m.stone.size());
            if (m.veins.size() >= 5 && per100 > 4.0 && now - m.lastFlag > 300000L) {
               m.lastFlag = now;
               this.flag(p, m.veins.size() + " valuable veins for only " + m.stone.size() + " stone in 20 min");
            }
         }

         return false;
      }

      private static final class Mining {
         final Deque<long[]> veins = new ArrayDeque<>();
         final Deque<Long> stone = new ArrayDeque<>();
         long lastFlag;
      }
   }
}
