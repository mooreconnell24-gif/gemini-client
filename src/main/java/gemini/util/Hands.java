package gemini.util;

import gemini.event.ActionPriority;
import gemini.event.EventBus;
import gemini.module.Module;

/**
 * Decides which module may change the selected hotbar slot or the offhand during the current tick.
 *
 * <p>Modules run in {@link ActionPriority} order, so the first claim in a tick normally comes from the most
 * important module; a later module of equal or lower priority is refused and skips its swap for this tick. A later
 * module of strictly higher priority (a hook that runs out of order, or a command) still wins. Code running outside
 * module dispatch, such as a command or a menu button, is never refused.
 */
public final class Hands {
   private static Module mainOwner;
   private static ActionPriority mainPriority;
   private static Module offOwner;
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
      if (module == null) {
         return true;
      } else if (mainOwner == null || mainOwner == module || EventBus.currentPriority().outranks(mainPriority)) {
         mainOwner = module;
         mainPriority = EventBus.currentPriority();
         return true;
      } else {
         return false;
      }
   }

   /** Whether the current module may change the offhand item right now. */
   public static boolean claimOffhand() {
      Module module = EventBus.current();
      if (module == null) {
         return true;
      } else if (offOwner == null || offOwner == module || EventBus.currentPriority().outranks(offPriority)) {
         offOwner = module;
         offPriority = EventBus.currentPriority();
         return true;
      } else {
         return false;
      }
   }

   /** The module that took the hotbar slot this tick, or null. */
   public static Module mainHandOwner() {
      return mainOwner;
   }

   public static Module offhandOwner() {
      return offOwner;
   }
}
