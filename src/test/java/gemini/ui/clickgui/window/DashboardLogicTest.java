package gemini.ui.clickgui.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardLogicTest {
   private static final String SUFFIX = " (Controls)";

   // ---- profiles -------------------------------------------------------------------------------------------

   @Test
   void activeProfileComesFirstThenNewest() {
      Map<String, Long> times = Map.of("pvp", 300L, "build", 500L, "travel", 100L, "old", ProfileList.UNKNOWN);
      List<ProfileList.Entry> list = ProfileList.build(List.of("pvp", "build", "travel", "old"), times::get, "travel", ProfileList.Sort.RECENT);
      assertEquals(List.of("travel", "build", "pvp", "old"), list.stream().map(ProfileList.Entry::name).toList());
      assertTrue(list.get(0).active());
      assertFalse(list.get(1).active());
   }

   @Test
   void nameSortIgnoresCase() {
      List<ProfileList.Entry> list = ProfileList.build(List.of("b", "A", "c"), n -> 0L, null, ProfileList.Sort.NAME);
      assertEquals(List.of("A", "b", "c"), list.stream().map(ProfileList.Entry::name).toList());
   }

   @Test
   void recentTiesFallBackToName() {
      List<ProfileList.Entry> list = ProfileList.build(List.of("zeta", "alpha"), n -> 42L, "missing", ProfileList.Sort.RECENT);
      assertEquals(List.of("alpha", "zeta"), list.stream().map(ProfileList.Entry::name).toList());
   }

   @Test
   void agoBuckets() {
      long now = 10_000_000_000L;
      assertEquals("just now", ProfileList.ago(now, now - 5_000L));
      assertEquals("5 min ago", ProfileList.ago(now, now - 300_000L));
      assertEquals("3 h ago", ProfileList.ago(now, now - 3L * 3_600_000L));
      assertEquals("2 d ago", ProfileList.ago(now, now - 2L * 86_400_000L));
      assertEquals("4 wk ago", ProfileList.ago(now, now - 28L * 86_400_000L));
      assertEquals("unknown", ProfileList.ago(now, ProfileList.UNKNOWN));
      assertEquals("just now", ProfileList.ago(now, now + 60_000L));
   }

   @Test
   void sortCycles() {
      assertEquals(ProfileList.Sort.NAME, ProfileList.Sort.RECENT.next());
      assertEquals(ProfileList.Sort.RECENT, ProfileList.Sort.NAME.next());
   }

   // ---- keybinds -------------------------------------------------------------------------------------------

   private static Map<String, Integer> snapshot() {
      Map<String, Integer> map = new LinkedHashMap<>();
      map.put("KillAura", 20);
      map.put("Flight", 9);
      map.put("speed", 20);
      map.put("Xray", 27);
      map.put("Jump" + SUFFIX, 44);
      map.put("Scaffold", 44);
      map.put("Drop" + SUFFIX, 20);
      map.put("Inventory" + SUFFIX, 8);
      map.put("Unbound", -1);
      return map;
   }

   @Test
   void groupsByKeyWithConflictsFirst() {
      List<KeybindGroups.Group> groups = KeybindGroups.build(snapshot(), SUFFIX, false);
      assertEquals(4, groups.size());
      KeybindGroups.Group first = groups.get(0);
      assertEquals(20, first.key());
      assertEquals(List.of("KillAura", "speed"), first.modules());
      assertEquals(List.of("Drop"), first.controls());
      assertTrue(first.conflict());
      assertEquals(3, first.size());
      KeybindGroups.Group second = groups.get(1);
      assertEquals(44, second.key());
      assertTrue(second.conflict());
      assertTrue(second.vanillaClash());
      assertEquals(List.of(9, 27), groups.subList(2, 4).stream().map(KeybindGroups.Group::key).toList());
      assertFalse(groups.get(2).conflict());
      assertEquals(2, KeybindGroups.conflictCount(groups));
      assertEquals(5, KeybindGroups.moduleCount(groups));
   }

   @Test
   void onlyConflictsFilter() {
      List<KeybindGroups.Group> groups = KeybindGroups.build(snapshot(), SUFFIX, true);
      assertEquals(List.of(20, 44), groups.stream().map(KeybindGroups.Group::key).toList());
   }

   @Test
   void vanillaOnlyKeysAreNotListed() {
      List<KeybindGroups.Group> groups = KeybindGroups.build(Map.of("Inventory" + SUFFIX, 8, "Chat" + SUFFIX, 8), SUFFIX, false);
      assertTrue(groups.isEmpty());
   }

   // ---- bots -----------------------------------------------------------------------------------------------

   @Test
   void ownerMatchesTaskKind() {
      assertEquals("Auto Mine Bot", BotTasks.owner("Mine", List.of("Auto Goto", "Auto Mine Bot")));
      assertNull(BotTasks.owner("Mine", List.of("Auto Goto")));
      assertNull(BotTasks.owner("Goto", List.of()));
      assertNull(BotTasks.owner(null, List.of("Auto Goto")));
   }

   @Test
   void genericLabelPicksFirstPathingModule() {
      assertEquals("Follow Bot", BotTasks.owner("Pathing", List.of("Auto Highway", "Follow Bot", "Auto Tunnel")));
      assertNull(BotTasks.owner("Pathing", List.of("Auto Highway")));
   }

   @Test
   void progressAndEta() {
      assertEquals(0.0F, BotTasks.progress(3, 0));
      assertEquals(0.5F, BotTasks.progress(5, 10));
      assertEquals(1.0F, BotTasks.progress(12, 10));
      assertEquals(10.0, BotTasks.etaSeconds(43.0, 4.3), 1.0E-9);
      assertEquals(-1.0, BotTasks.etaSeconds(43.0, 0.1));
      assertEquals(-1.0, BotTasks.etaSeconds(Double.NaN, 4.0));
   }

   @Test
   void durations() {
      assertEquals("45s", BotTasks.formatDuration(44.6));
      assertEquals("3m 05s", BotTasks.formatDuration(185.0));
      assertEquals("1h 02m", BotTasks.formatDuration(3720.0));
      assertEquals("--", BotTasks.formatDuration(-1.0));
      assertEquals("--", BotTasks.formatDuration(Double.POSITIVE_INFINITY));
   }

   @Test
   void smoothing() {
      assertEquals(4.0, BotTasks.smooth(Double.NaN, 4.0, 0.2));
      assertEquals(2.0, BotTasks.smooth(0.0, 10.0, 0.2), 1.0E-9);
   }
}
