package gemini.ui.clickgui.window;

import gemini.input.KeybindConflicts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Groups a keybind snapshot ({@code KeybindReport.snapshot}: module names plus vanilla controls whose names
 * carry a suffix) by key for the keybind page. Only keys that at least one module uses are listed; vanilla
 * controls appear in a group only when they share its key. Whether a key conflicts is decided by
 * {@link KeybindConflicts#find}, the same rule {@code .binds} uses.
 */
public final class KeybindGroups {
   private static final Comparator<String> NAME_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

   private KeybindGroups() {
   }

   /** One key: the modules bound to it and the vanilla controls (names without the suffix) that use it too. */
   public record Group(int key, List<String> modules, List<String> controls, boolean conflict) {
      public int size() {
         return this.modules.size() + this.controls.size();
      }

      /** True when a vanilla control uses this key as well. */
      public boolean vanillaClash() {
         return !this.controls.isEmpty();
      }
   }

   /**
    * Conflicting keys first, then by key code. With {@code onlyConflicts} the keys used by a single module and
    * nothing else are left out.
    */
   public static List<Group> build(Map<String, Integer> snapshot, String controlsSuffix, boolean onlyConflicts) {
      Set<Integer> conflicting = KeybindConflicts.find(snapshot).keySet();
      Map<Integer, List<String>> modules = new TreeMap<>();
      Map<Integer, List<String>> controls = new TreeMap<>();
      for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
         Integer key = entry.getValue();
         if (key == null || !KeybindConflicts.isBound(key)) {
            continue;
         }

         String name = entry.getKey();
         if (controlsSuffix != null && !controlsSuffix.isEmpty() && name.endsWith(controlsSuffix)) {
            controls.computeIfAbsent(key, k -> new ArrayList<>()).add(name.substring(0, name.length() - controlsSuffix.length()));
         } else {
            modules.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
         }
      }

      List<Group> groups = new ArrayList<>();
      for (Map.Entry<Integer, List<String>> entry : modules.entrySet()) {
         int key = entry.getKey();
         boolean conflict = conflicting.contains(key);
         if (onlyConflicts && !conflict) {
            continue;
         }

         List<String> names = new ArrayList<>(entry.getValue());
         names.sort(NAME_ORDER);
         List<String> vanilla = new ArrayList<>(controls.getOrDefault(key, List.of()));
         vanilla.sort(NAME_ORDER);
         groups.add(new Group(key, List.copyOf(names), List.copyOf(vanilla), conflict));
      }

      groups.sort(Comparator.comparing((Group g) -> !g.conflict()).thenComparingInt(Group::key));
      return groups;
   }

   public static int conflictCount(List<Group> groups) {
      int count = 0;
      for (Group group : groups) {
         if (group.conflict()) {
            count++;
         }
      }

      return count;
   }

   /** Number of modules bound across the groups. */
   public static int moduleCount(List<Group> groups) {
      int count = 0;
      for (Group group : groups) {
         count += group.modules().size();
      }

      return count;
   }
}
