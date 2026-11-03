package gemini.util;

import gemini.event.ActionPriority;
import gemini.event.EventBus;
import gemini.module.Module;

/**
 * Decides who may change the selected hotbar slot or the offhand during the current tick.
 *
 * <p>Modules run in {@link ActionPriority} order, so the first claim in a tick normally comes from the most
 * important module; a later claim of equal or lower priority is refused and that caller skips its swap for this
 * tick. A later claim of strictly higher priority (a hook that runs out of order, or a command) still wins. Code
 * running outside module dispatch that gives no priority, such as a command or a menu button, is never refused;
 * the pathfinder, which ticks after the modules, claims explicitly at {@link ActionPriority#BOT} so that it never
 * takes the hand away from a totem swap, a gapple or a tool switch.
 */
public final class Hands {
   private static Object mainOwner;
   private static ActionPriority mainPriority;
   private static Object offOwner;
   private static ActionPriority offPriority;

   private Hands() {
   }

   /** Clears this tick's claims; called by the module manager before the modules tick. */
   public static void beginTick() {
      mainOwner = null;
      mainPriority = null;
      offOwner = null;
      offPriority = null;
   }

   /** Whether the current module may change the selected hotbar slot right now. */
   public static boolean claimMainHand() {
      Module module = EventBus.current();
      return module == null ? true : claimMainHand(module, EventBus.currentPriority());
   }

   /** A claim by something other than the running module (the pathfinder, a scheduled action) at an explicit priority. */
   public static boolean claimMainHand(Object owner, ActionPriority priority) {
      if (mainOwner == null || mainOwner == owner || priority != null && priority.outranks(mainPriority)) {
         mainOwner = owner;
         mainPriority = priority;
         return true;
      } else {
         return false;
      }
   }

   /** Whether the current module may change the offhand item right now. */
   public static boolean claimOffhand() {
      Module module = EventBus.current();
      return module == null ? true : claimOffhand(module, EventBus.currentPriority());
   }

   public static boolean claimOffhand(Object owner, ActionPriority priority) {
      if (offOwner == null || offOwner == owner || priority != null && priority.outranks(offPriority)) {
         offOwner = owner;
         offPriority = priority;
         return true;
      } else {
         return false;
      }
   }

   /** Whoever took the hotbar slot this tick (a module, or another claimant such as the pathfinder), or null. */
   public static Object mainHandOwner() {
      return mainOwner;
   }

   public static Object offhandOwner() {
      return offOwner;
   }
}
