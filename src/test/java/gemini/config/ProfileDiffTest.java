package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileDiffTest {
   private static JsonObject module(boolean enabled, int key, Object... settings) {
      JsonObject module = new JsonObject();
      module.addProperty("enabled", enabled);
      module.addProperty("keybind", key);
      JsonObject values = new JsonObject();
      for (int i = 0; i + 1 < settings.length; i += 2) {
         Object value = settings[i + 1];
         if (value instanceof Number n) {
            values.addProperty((String)settings[i], n);
         } else if (value instanceof Boolean b) {
            values.addProperty((String)settings[i], b);
         } else {
            values.addProperty((String)settings[i], String.valueOf(value));
         }
      }

      module.add("settings", values);
      return module;
   }

   private static JsonObject current() {
      JsonObject all = new JsonObject();
      all.add("KillAura", module(false, -1, "Range", 4.2, "Rotate", true));
      all.add("Flight", module(true, 33, "Speed", 1.0));
      all.add("Auto Totem", module(true, -1, "Health", 10));
      return all;
   }

   @Test
   void identicalProfileChangesNothing() {
      ProfileDiff diff = ProfileDiff.compute(current(), current());
      assertTrue(diff.empty());
      assertEquals(3, diff.modules());
      assertTrue(diff.summary().contains("identical"));
   }

   @Test
   void reportsSwitchesBindsAndSettings() {
      JsonObject incoming = new JsonObject();
      incoming.add("KillAura", module(true, 20, "Range", 5.0, "Rotate", true));
      incoming.add("Flight", module(false, 33, "Speed", 1.0, "Unknown Setting", 3));
      // Matched by normalized id, like the loader does.
      incoming.add("autototem", module(true, -1, "Health", 12));
      incoming.add("Removed Module", module(true, -1));
      ProfileDiff diff = ProfileDiff.compute(current(), incoming);
      assertEquals(4, diff.modules());
      assertEquals(List.of("KillAura"), diff.switchedOn());
      assertEquals(List.of("Flight"), diff.switchedOff());
      assertEquals(List.of("KillAura"), diff.rebound());
      assertEquals(List.of("Removed Module"), diff.unknown());
      assertEquals(2, diff.settingsChanged(), "Range and Health; a setting this build lacks changes nothing");
      assertEquals(2, diff.modulesWithSettingChanges());
      assertFalse(diff.empty());
      String summary = diff.summary();
      assertTrue(summary.contains("4 modules"), summary);
      assertTrue(summary.contains("1 on, 1 off"), summary);
      assertTrue(summary.contains("1 key bind"), summary);
      assertTrue(summary.contains("2 settings in 2 modules"), summary);
   }

   @Test
   void missingEnabledMeansOffAndUnboundKeysCompareEqual() {
      JsonObject incoming = new JsonObject();
      JsonObject aura = new JsonObject();
      aura.addProperty("keybind", 0);
      incoming.add("KillAura", aura);
      JsonObject flight = new JsonObject();
      incoming.add("Flight", flight);
      ProfileDiff diff = ProfileDiff.compute(current(), incoming);
      assertTrue(diff.switchedOn().isEmpty());
      assertEquals(List.of("Flight"), diff.switchedOff(), "the loader treats a missing switch as off");
      assertTrue(diff.rebound().isEmpty(), "0 and -1 are both unbound; no keybind key keeps the current one");
   }

   @Test
   void namesAreCapped() {
      assertEquals("none", ProfileDiff.names(List.of(), 3));
      assertEquals("a, b", ProfileDiff.names(List.of("a", "b"), 3));
      assertEquals("a, b +2 more", ProfileDiff.names(List.of("a", "b", "c", "d"), 2));
   }

   @Test
   void survivesNullsAndJunk() {
      JsonObject incoming = new JsonObject();
      incoming.addProperty("junk", 5);
      ProfileDiff diff = ProfileDiff.compute(null, incoming);
      assertEquals(0, diff.modules());
      assertTrue(ProfileDiff.compute(current(), null).empty());
   }
}
