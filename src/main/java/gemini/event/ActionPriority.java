package gemini.event;

import gemini.module.Category;

/**
 * Who goes first when modules compete for the same thing in one tick: the player's hands (hotbar slot and
 * offhand), the server-side rotation, and the order the hooks run in. Higher priorities run first and win claims.
 */
public enum ActionPriority {
   /** Totems, gapples, clutches, anti-void: anything that keeps the player alive. */
   CRITICAL_SURVIVAL(40),
   /** Attacking, blocking, crystals, combat switching. */
   COMBAT(30),
   /** Flight, speed, scaffold-style movement. */
   MOVEMENT(20),
   /** Farming, building, tools, inventory tidying, cosmetics. */
   WORLD_UTILITY(10),
   /** The pathfinder and its bots; everything else overrides them. */
   BOT(0);

   private final int level;

   private ActionPriority(int level) {
      this.level = level;
   }

   public int level() {
      return this.level;
   }

   public boolean outranks(ActionPriority other) {
      return other == null || this.level > other.level;
   }

   /** The priority a module gets from its category unless it overrides {@code priority()}. */
   public static ActionPriority defaultFor(Category category) {
      return switch (category) {
         case COMBAT -> COMBAT;
         case MOVEMENT -> MOVEMENT;
         default -> WORLD_UTILITY;
      };
   }

   /** Maps the old integer rotation levels (0 / 5 / 8 / 10) used before this enum existed. */
   public static ActionPriority fromLegacy(int level) {
      if (level >= 7) {
         return COMBAT;
      } else if (level >= 4) {
         return MOVEMENT;
      } else {
         return WORLD_UTILITY;
      }
   }
}
