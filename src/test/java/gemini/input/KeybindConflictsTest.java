package gemini.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The conflict rules behind every warning path: the bind paths, profile loads and the shared count. */
class KeybindConflictsTest {
   private static final String SUFFIX = " (Controls)";

   private static Map<String, Integer> binds(Object... pairs) {
      Map<String, Integer> map = new LinkedHashMap<>();
      for (int i = 0; i + 1 < pairs.length; i += 2) {
         map.put((String)pairs[i], (Integer)pairs[i + 1]);
      }

      return map;
   }

   @Test
   void bindPathWarnsAboutModulesAndVanillaControls() {
      Map<String, Integer> snapshot = binds("KillAura", 20, "Flight", 20, "Jump" + SUFFIX, 20, "Speed", 21);
      // What KeybindReport.warning reports for KillAura bound to 20.
      assertEquals(List.of("Flight", "Jump" + SUFFIX), KeybindConflicts.others(snapshot, "KillAura", 20));
      assertTrue(KeybindConflicts.others(snapshot, "Speed", 21).isEmpty(), "a free key warns about nothing");
      assertTrue(KeybindConflicts.others(snapshot, "Speed", -1).isEmpty(), "unbinding never warns");
   }

   @Test
   void loadAnnouncesOnlyNewConflicts() {
      Map<String, Integer> before = binds("KillAura", 20, "Flight", 20, "Speed", 21);
      Map<String, Integer> sameAsBefore = binds("KillAura", 20, "Flight", 20, "Speed", 21);
      assertTrue(KeybindConflicts.introduced(before, sameAsBefore).isEmpty(), "conflicts the player already had are not repeated");
      Map<String, Integer> after = binds("KillAura", 20, "Flight", 20, "Speed", 22, "Nuker", 22);
      Map<Integer, List<String>> fresh = KeybindConflicts.introduced(before, after);
      assertEquals(Map.of(22, List.of("Nuker", "Speed")), fresh);
      Map<String, Integer> grown = binds("KillAura", 20, "Flight", 20, "Scaffold", 20, "Speed", 21);
      assertEquals(List.of("Flight", "KillAura", "Scaffold"), KeybindConflicts.introduced(before, grown).get(20), "a conflict that gained a module is new");
      Map<String, Integer> shrunk = binds("KillAura", 20, "Speed", 21);
      assertTrue(KeybindConflicts.introduced(before, shrunk).isEmpty(), "fixing a conflict is not a new one");
   }

   @Test
   void sharedCountIgnoresVanillaOnlyClashes() {
      Map<String, Integer> snapshot = binds(
         "KillAura", 20, "Flight", 20,
         "Speed", 30, "Sprint" + SUFFIX, 30,
         "Inventory" + SUFFIX, 8, "Drop" + SUFFIX, 8,
         "Nuker", 40
      );
      assertEquals(3, KeybindConflicts.find(snapshot).size());
      assertEquals(2, KeybindConflicts.moduleConflictCount(snapshot, SUFFIX), "two vanilla controls on one key are vanilla's business");
      assertEquals(3, KeybindConflicts.moduleConflictCount(snapshot, null));
   }

   @Test
   void unboundKeysNeverConflict() {
      Map<String, Integer> snapshot = binds("A", -1, "B", -1, "C", 0, "D", 0);
      assertTrue(KeybindConflicts.find(snapshot).isEmpty());
      assertEquals(0, KeybindConflicts.moduleConflictCount(snapshot, SUFFIX));
   }
}
