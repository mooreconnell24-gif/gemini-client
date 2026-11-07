package com.inferno.fabric.checks;

import java.util.HashMap;
import java.util.Map;

/**
 * What a client sent during one of its own ticks. A vanilla client ends every tick with a
 * ServerboundClientTickEndPacket (Minecraft#tick), so the packets between two of those belong to one
 * client tick. Vanilla sends at most one movement packet, one sprint command and one hotbar change per
 * tick (plus one extra movement packet when it confirms a teleport), which is what these counters test.
 */
public final class ClientTick {
   /** When this client tick started (the previous tick-end packet), server clock. */
   public long start;
   public int moves;
   public int sprintCommands;
   public int attacks;
   public int places;
   public int uses;
   public int slotChanges;
   public int digStarts;
   public int clicks;
   public boolean teleportAccepted;
   public final Map<Integer, Integer> attacksPerTarget = new HashMap<>();

   void reset(long now) {
      this.start = now;
      this.moves = 0;
      this.sprintCommands = 0;
      this.attacks = 0;
      this.places = 0;
      this.uses = 0;
      this.slotChanges = 0;
      this.digStarts = 0;
      this.clicks = 0;
      this.teleportAccepted = false;
      this.attacksPerTarget.clear();
   }

   /** Highest number of attacks on one entity during this tick. */
   public int maxAttacksOnOneTarget() {
      int max = 0;

      for (int n : this.attacksPerTarget.values()) {
         max = Math.max(max, n);
      }

      return max;
   }
}
