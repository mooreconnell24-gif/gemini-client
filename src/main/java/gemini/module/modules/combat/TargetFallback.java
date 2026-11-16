package gemini.module.modules.combat;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * "Go for the priority target, and if nothing can be done to it, the next one": the second-stage filter of the
 * placement auras, whose real check (a crystal spot with enough damage, a surround block in reach, a piston
 * plan) needs a whole placement search and so cannot run inside the target pick for every entity.
 *
 * <p>Kept free of game classes so the order can be unit tested. The picker is asked for the best candidate that
 * passes an exclusion filter; each candidate the search rejects is added to that filter, so the next ask returns
 * the next one in priority order.
 */
public final class TargetFallback {
   /** How many enemies the placement auras try in one tick before giving up until the next. */
   public static final int TRIES = 3;

   private TargetFallback() {
   }

   /**
    * The first candidate, in the order {@code pick} hands them out, that {@code viable} accepts, or null.
    *
    * @param pick returns the best candidate passing the given filter, or null when none is left
    * @param viable the expensive check; called once per candidate tried, so it may remember what it worked out
    * @param tries how many candidates to try at most
    */
   public static <T> T first(Function<Predicate<T>, T> pick, Predicate<T> viable, int tries) {
      Object[] tried = new Object[Math.max(0, tries)];
      Predicate<T> untried = candidate -> {
         for (Object t : tried) {
            if (t == candidate) {
               return false;
            }
         }

         return true;
      };
      for (int i = 0; i < tried.length; i++) {
         T candidate = pick.apply(untried);
         if (candidate == null) {
            return null;
         }

         if (viable.test(candidate)) {
            return candidate;
         }

         tried[i] = candidate;
      }

      return null;
   }
}
