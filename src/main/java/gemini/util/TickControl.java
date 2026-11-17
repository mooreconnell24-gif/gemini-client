package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

/**
 * The single-player world's tick rate and freeze switch, with a record of which module set each of them.
 *
 * <p>Timer and Tick Freeze both drive the integrated server's tick manager. They used to send {@code /tick}
 * commands through {@link SpCommands}, which refuses to run once the player is gone or the world is no longer a
 * private one; that is exactly when a module is switched off on leaving or on opening the world to LAN, so the
 * world could be left running fast or frozen with nothing to put it back. The calls here go straight to the tick
 * manager: <em>changing</em> the clock still needs {@link SingleplayerGate#allowed()}, but handing it back to
 * vanilla (20 ticks per second, not frozen) only needs the integrated server to exist.
 *
 * <p>Each of the two switches remembers its owner, so a module only undoes what it did itself, and a second module
 * that takes a switch over is not undone by the first one being switched off later.
 */
public final class TickControl {
   public static final float NORMAL_RATE = 20.0F;
   private static Object rateOwner;
   private static Object freezeOwner;

   private TickControl() {
   }

   /** Sets the game clock for {@code owner}. The latest caller owns the rate. False when Gemini may not act right now. */
   public static boolean setRate(Object owner, float ticksPerSecond) {
      MinecraftServer server = server();
      if (server == null || !SingleplayerGate.allowed()) {
         return false;
      }

      float rate = Math.max(1.0F, ticksPerSecond);
      rateOwner = owner;
      server.execute(() -> server.tickRateManager().setTickRate(rate));
      return true;
   }

   /** Puts the clock back to 20 ticks per second if {@code owner} was the last to set it. */
   public static void releaseRate(Object owner) {
      if (rateOwner == owner) {
         rateOwner = null;
         MinecraftServer server = server();
         if (server != null) {
            server.execute(() -> server.tickRateManager().setTickRate(NORMAL_RATE));
         }
      }
   }

   /** Whether {@code owner} was the last to set the clock and has not released it. */
   public static boolean ownsRate(Object owner) {
      return rateOwner == owner;
   }

   /** Freezes the world for {@code owner}. False when Gemini may not act right now. */
   public static boolean freeze(Object owner) {
      MinecraftServer server = server();
      if (server == null || !SingleplayerGate.allowed()) {
         return false;
      }

      freezeOwner = owner;
      server.execute(() -> server.tickRateManager().setFrozen(true));
      return true;
   }

   /** Unfreezes the world if {@code owner} froze it. */
   public static void unfreeze(Object owner) {
      if (freezeOwner == owner) {
         freezeOwner = null;
         MinecraftServer server = server();
         if (server != null) {
            server.execute(() -> {
               ServerTickRateManager manager = server.tickRateManager();
               manager.stopStepping();
               manager.setFrozen(false);
            });
         }
      }
   }

   /** Advances a world frozen by {@code owner} by a number of ticks. */
   public static boolean step(Object owner, int ticks) {
      MinecraftServer server = server();
      if (freezeOwner != owner || server == null || !SingleplayerGate.allowed()) {
         return false;
      }

      int count = Math.max(1, ticks);
      server.execute(() -> server.tickRateManager().stepGameIfPaused(count));
      return true;
   }

   private static MinecraftServer server() {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      return server != null && server.isRunning() ? server : null;
   }
}
