package com.inferno.fabric.checks;

import java.util.Iterator;
import net.minecraft.server.level.ServerPlayer;

public final class InventoryChecks {
   private InventoryChecks() {
   }

   public static final class FastClick extends Check {
      public FastClick() {
         super("inventory-fastclick", "Inventory (Fast Click)", Check.Category.WORLD, "Taking items too soon after opening a chest (ChestStealer).");
      }

      @Override
      public void onClickSlot(ServerPlayer p, PlayerState s) {
         if (s.containerOpen && !s.firstClickDone) {
            s.firstClickDone = true;
            long first = System.currentTimeMillis() - s.containerOpenTime;
            if (first < 70L) {
               if (this.buffer(s, 1.0) >= 2.0) {
                  this.flag(p, "took an item " + first + " ms after opening");
               }
            } else {
               this.decay(s, 0.5);
            }
         }
      }
   }

   public static final class InvMove extends Check {
      public InvMove() {
         super(
            "inventory-move", "Inventory (Move)", Check.Category.MOVEMENT, "Moving, sprinting or looking around with an inventory open (InvMove, AutoArmor)."
         );
      }

      @Override
      public void onClickSlot(ServerPlayer p, PlayerState s) {
         long now = System.currentTimeMillis();
         if (!CheckEngine.special(p)
            && !s.exempt()
            && p.hurtTime <= 0
            && !p.isInWater()
            && !p.isInLava()
            && !CheckEngine.blocksNear(p.level(), p.getBoundingBox().expandTowards(0.0, -1.0, 0.0), "ice")) {
            boolean openedRecently = s.containerOpen && now - s.containerOpenTime < 500L;
            boolean sprinting = p.isSprinting() && now - s.lastPositionChange < 60L && s.lastHorizontal > 0.2;
            boolean turning = turnedJustNow(s, now);
            if (!openedRecently && (sprinting || turning)) {
               if (this.buffer(s, 1.0) >= 3.0) {
                  this.buffer(s, -3.0);
                  this.flag(p, sprinting ? "sprinting while clicking in an inventory" : "turning while clicking in an inventory");
               }
            } else {
               this.decay(s, 0.5);
            }
         }
      }

      private static boolean turnedJustNow(PlayerState s, long now) {
         if (s.rotations.size() < 2) {
            return false;
         }

         Iterator<double[]> it = s.rotations.iterator();
         double[] a = it.next();
         double[] b = it.next();
         return now - a[0] < 60.0 && Math.abs(Move.wrap((float)(a[1] - b[1]))) + Math.abs(a[2] - b[2]) > 2.0;
      }
   }
}
