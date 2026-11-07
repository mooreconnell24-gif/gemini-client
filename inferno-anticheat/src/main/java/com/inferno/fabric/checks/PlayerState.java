package com.inferno.fabric.checks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class PlayerState {
   public double fromX;
   public double fromY;
   public double fromZ;
   public float fromYaw;
   public float fromPitch;
   public long joinTime = System.currentTimeMillis();
   public long exemptUntil;
   public long lastSetback;
   public long velocityChange;
   public long lastPositionChange = System.currentTimeMillis();
   public long lastAttack;
   public long lastSwing;
   public boolean containerOpen;
   public long containerOpenTime;
   public boolean firstClickDone;
   public long totemPop;
   public boolean rawLook;
   public float rawPitch;
   public BlockPos digPos;
   public long digTime;
   public final Deque<Long> unswungAttacks = new ArrayDeque<>();
   public String lastTarget;
   public boolean lastGround = true;
   public volatile double lastDy;
   public volatile int airTicks;
   public volatile String lastFlag = "";
   public volatile long lastFlagTime;
   public double lastHorizontal;
   public double safeX = Double.NaN;
   public double safeY;
   public double safeZ;
   public boolean lockActive;
   public long lockUntil;
   public double lockX;
   public double lockY;
   public double lockZ;
   public int cleanGroundTicks;
   public String lockReason = "";
   public final Deque<double[]> rotations = new ArrayDeque<>();
   public final Map<String, Object> data = new HashMap<>();
   public final Map<String, Double> buffers = new HashMap<>();

   // --- Added for the LiquidBounce coverage checks -------------------------------------------
   /** Counters for the client tick in progress (reset at every ServerboundClientTickEndPacket). */
   public final ClientTick tick = new ClientTick();
   public long lastTickEnd;
   public long lastTeleportAccept;
   public long lastInputChange;
   public long lastJumpInput;
   public long lastClick;
   public long lastBlockPlace;
   public long lastItemUse;
   public long deathTime;
   public long lastVelocity;
   public long lastFireworkUse;
   /** Number of client ticks (tick-end packets) received since joining. */
   public long clientTicks;
   /** Position the last movement packet asked for (before the server applied it). */
   public boolean rawHasPos;
   public double rawX;
   public double rawY;
   public double rawZ;

   /**
    * Knockback, an explosion or a teleport within {@code ms}. Deliberately not {@link #exempt()}: the
    * engine exempts gliding / riptiding / levitating players on every move, and checks aimed at exactly
    * those states must still run.
    */
   public boolean recentlyPushed(long ms) {
      long now = System.currentTimeMillis();
      return now - this.lastVelocity < ms || now - this.velocityChange < ms || now - this.lastSetback < ms;
   }

   public boolean exempt() {
      return System.currentTimeMillis() < this.exemptUntil;
   }

   public void exempt(long ms) {
      this.exemptUntil = Math.max(this.exemptUntil, System.currentTimeMillis() + ms);
   }

   public void addRotation(float yaw, float pitch) {
      this.rotations.addFirst(new double[]{System.currentTimeMillis(), yaw, pitch});

      while (this.rotations.size() > 10) {
         this.rotations.removeLast();
      }
   }
}
