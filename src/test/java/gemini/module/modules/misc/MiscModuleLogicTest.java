package gemini.module.modules.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pure parts of the misc modules. */
class MiscModuleLogicTest {
   @Test
   void disableOthersLeavesTheChosenPresetsModulesAlone() {
      Set<String> off = Presets.othersOf("PvP");
      assertTrue(off.contains("Flight"), "a Travel module is turned off");
      assertTrue(off.contains("X-Ray"), "an Explore module is turned off");
      assertFalse(off.contains("Sprint"), "Sprint is part of PvP too, so it stays on");
      assertFalse(off.contains("Kill Aura"), "the chosen preset's own module");
      assertFalse(off.contains("HUD"), "the HUD is a settings page and is never switched off");
   }

   @Test
   void everyPresetLeavesItsOwnModulesOn() {
      for (String preset : new String[]{"General", "Performance", "PvP", "Explore", "Build", "Travel"}) {
         Set<String> off = Presets.othersOf(preset);
         assertFalse(off.isEmpty(), preset + " turns something off");
         assertFalse(off.contains("HUD"));
      }

      assertFalse(Presets.othersOf("Travel").contains("Step"));
      assertTrue(Presets.othersOf("Build").contains("Step"));
   }
}
