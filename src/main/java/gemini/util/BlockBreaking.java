package gemini.util;

/**
 * Keeps module-driven block breaking alive across ticks.
 *
 * <p>Every tick the attack key is not held, vanilla calls {@code MultiPlayerGameMode.stopDestroyBlock()}, which
 * aborts the block in progress and resets its damage. A module that breaks blocks itself (Nuker, VeinMiner, the
 * pathfinder...) calls {@link #keep()} on each tick it drives {@code continueDestroyBlock}; the game-mode mixin
 * then skips vanilla's abort for the following tick, so damage accumulates and blocks that need more than one
 * tick actually break. A module that wants to stop calls {@link #release()} before its own
 * {@code stopDestroyBlock()}.
 */
public final class BlockBreaking {
   private static int tick;
   private static int keepTick = Integer.MIN_VALUE;

   private BlockBreaking() {
   }

   /** Called once per client tick by the module manager. */
   public static void tick() {
      tick++;
   }

   /** Call on every tick a module is breaking a block itself. */
   public static void keep() {
      keepTick = tick;
   }

   /** Lets vanilla abort breaking again; call before stopping on purpose. */
   public static void release() {
      keepTick = Integer.MIN_VALUE;
   }

   /** Whether a module kept the break alive this tick, so vanilla's automatic abort must be skipped. */
   public static boolean active() {
      return tick - keepTick <= 0;
   }
}
