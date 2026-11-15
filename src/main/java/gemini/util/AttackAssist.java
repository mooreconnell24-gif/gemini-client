package gemini.util;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Critical hits and mace smashes, decided where the damage is calculated.
 *
 * <p>Both used to be faked by sending extra position packets right before the attack, so the server believed the
 * player was falling. Minecraft 26.3 disconnects a client that sends more than one position packet in a tick, so
 * that trick now throws the player out of their own world. Gemini only runs against its own integrated server,
 * which lives in the same process, so the honest way is open: {@link #before} runs on the server thread at the top
 * of {@code Player.attack} and gives the world's owner the falling state for the length of that one call;
 * {@link #after} puts it back.
 *
 * <p>The switches are set by the Criticals and MaceKill modules while they are enabled. Only the player whose id
 * was registered is touched, so a guest in a world opened to LAN is never affected.
 */
public final class AttackAssist {
   private static volatile UUID owner;
   private static volatile boolean criticals;
   private static volatile double maceFall;
   // Server thread only: what to put back when the attack call returns.
   private static boolean restore;
   private static boolean wasOnGround;
   private static double fallBefore;

   private AttackAssist() {
   }

   /** Called by Criticals every tick it is on in its server-side mode, and with false when it is not. */
   public static void criticals(UUID player, boolean on) {
      if (on) {
         owner = player;
      }

      criticals = on;
   }

   /** Called by MaceKill with the fall height to credit, or 0 to switch it off. */
   public static void maceFall(UUID player, double blocks) {
      if (blocks > 0.0) {
         owner = player;
      }

      maceFall = blocks;
   }

   /** Top of {@code Player.attack}, on whichever thread owns that player. */
   public static void before(Player attacker) {
      restore = false;
      if (!(attacker instanceof ServerPlayer player) || !player.getUUID().equals(owner)) {
         return;
      }

      boolean mace = maceFall > 0.0 && player.getMainHandItem().is(Items.MACE);
      if (!mace && !criticals) {
         return;
      }

      // The same situations vanilla refuses a critical in; faking a fall while swimming or climbing would look wrong.
      if (!player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger() || player.isFallFlying()) {
         return;
      }

      restore = true;
      wasOnGround = player.onGround();
      fallBefore = player.fallDistance;
      player.fallDistance = Math.max(player.fallDistance, mace ? maceFall : 0.1);
      player.setOnGround(false);
   }

   /** End of {@code Player.attack}. A mace smash resets the fall distance itself; that result is kept. */
   public static void after(Player attacker) {
      if (restore && attacker instanceof ServerPlayer player) {
         restore = false;
         player.setOnGround(wasOnGround);
         player.fallDistance = Math.min(player.fallDistance, fallBefore);
      }
   }
}
