package gemini.diags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MixinReport {
   private static final List<String> MISSED = new ArrayList<>();
   private static boolean announced;

   private MixinReport() {
   }

   public static void missed(String target, String handler) {
      synchronized (MISSED) {
         MISSED.add(target.substring(target.lastIndexOf('.') + 1) + " <- " + handler);
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
