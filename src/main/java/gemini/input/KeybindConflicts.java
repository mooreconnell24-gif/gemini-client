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
