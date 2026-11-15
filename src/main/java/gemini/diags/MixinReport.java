package gemini.diags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MixinReport {
   private static final List<String> MISSED = new ArrayList<>();
   private static final Set<String> APPLIED = new HashSet<>();
   private static boolean announced;

   private MixinReport() {
   }

   public static void missed(String target, String handler) {
      synchronized (MISSED) {
         MISSED.add(target.substring(target.lastIndexOf('.') + 1) + " <- " + handler);
      }
   }

   /** Records that a mixin class was woven into its target (called once per target as classes load). */
   public static void applied(String mixinClassName) {
      synchronized (MISSED) {
         APPLIED.add(mixinClassName);
      }
   }

   /** How many distinct mixin classes have been applied so far; targets that have not loaded yet are not counted. */
   public static int appliedCount() {
      synchronized (MISSED) {
         return APPLIED.size();
      }
   }

   /** How many mixins or single injections found no target. */
   public static int failedCount() {
      synchronized (MISSED) {
         return MISSED.size();
      }
   }

   public static List<String> missed() {
      synchronized (MISSED) {
         return Collections.unmodifiableList(new ArrayList<>(MISSED));
      }
   }

   public static boolean shouldAnnounce() {
      synchronized (MISSED) {
         if (!announced && !MISSED.isEmpty()) {
            announced = true;
            return true;
         } else {
            return false;
         }
      }
   }
}
