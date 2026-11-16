package gemini.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class KeybindConflicts {
   public static final int UNBOUND = -1;
   private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

   private KeybindConflicts() {
   }

   public static boolean isBound(int key) {
      return key > 0;
   }

   public static Map<Integer, List<String>> find(Map<String, Integer> bindings) {
      Map<Integer, List<String>> byKey = new TreeMap<>();

      for (Entry<String, Integer> entry : bindings.entrySet()) {
         Integer key = entry.getValue();
         if (key != null && isBound(key)) {
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
         }
      }

      Map<Integer, List<String>> conflicts = new LinkedHashMap<>();

      for (Entry<Integer, List<String>> entryx : byKey.entrySet()) {
         if (entryx.getValue().size() > 1) {
            List<String> names = new ArrayList<>(entryx.getValue());
            names.sort(NAME_ORDER);
            conflicts.put(entryx.getKey(), List.copyOf(names));
         }
      }

      return conflicts;
   }

   /**
    * Keys that conflict in {@code after} in a way they did not in {@code before}: a key that was clean and now has
    * several users, or a conflicting key that gained a user. Used after a config or profile load to announce only
    * the clashes that load brought in, not the ones the player already lived with.
    */
   public static Map<Integer, List<String>> introduced(Map<String, Integer> before, Map<String, Integer> after) {
      Map<Integer, List<String>> was = find(before);
      Map<Integer, List<String>> now = find(after);
      Map<Integer, List<String>> fresh = new LinkedHashMap<>();
      for (Entry<Integer, List<String>> entry : now.entrySet()) {
         List<String> previous = was.get(entry.getKey());
         if (previous == null || !previous.containsAll(entry.getValue())) {
            fresh.put(entry.getKey(), entry.getValue());
         }
      }

      return fresh;
   }

   /**
    * How many keys conflict with at least one module among their users. Vanilla controls in the snapshot carry
    * {@code controlsSuffix}; two vanilla controls sharing a key are vanilla's business and are not counted. This
    * is the one number the title status bar, the pause dock, the dashboard badge and the session toast all show.
    */
   public static int moduleConflictCount(Map<String, Integer> snapshot, String controlsSuffix) {
      int count = 0;
      for (List<String> names : find(snapshot).values()) {
         for (String name : names) {
            if (controlsSuffix == null || controlsSuffix.isEmpty() || !name.endsWith(controlsSuffix)) {
               count++;
               break;
            }
         }
      }

      return count;
   }

   public static List<String> others(Map<String, Integer> bindings, String self, int key) {
      List<String> clashes = new ArrayList<>();
      if (isBound(key)) {
         for (Entry<String, Integer> entry : bindings.entrySet()) {
            if (!entry.getKey().equals(self) && entry.getValue() != null && entry.getValue() == key) {
               clashes.add(entry.getKey());
            }
         }

         clashes.sort(NAME_ORDER);
      }

      return clashes;
   }
}
