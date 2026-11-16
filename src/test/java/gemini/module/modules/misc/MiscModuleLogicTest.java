package gemini.module.modules.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The pure parts of the misc modules. */
class MiscModuleLogicTest {
   @Test
   void disableOthersLeavesTheChosenPresetsModulesAlone() {
      Set<String> off = Presets.othersOf("PvP");
      assertTrue(off.contains("Flight"), "a Travel module is turned off");
      assertTrue(off.contains("Xray"), "an Explore module is turned off");
      assertFalse(off.contains("Sprint"), "Sprint is part of PvP too, so it stays on");
      assertFalse(off.contains("KillAura"), "the chosen preset's own module");
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

   @Test
   void shortCorrectionsAreSetbacksTeleportsAreNot() {
      Set<Relative> absolute = EnumSet.noneOf(Relative.class);
      assertTrue(FlagCheck.isSetback(10.0, 64.0, 10.0, new Vec3(9.2, 64.0, 10.0), absolute), "pulled back under a block");
      assertFalse(FlagCheck.isSetback(10.0, 64.0, 10.0, new Vec3(500.0, 70.0, -30.0), absolute), "a /tp far away");
      assertFalse(FlagCheck.isSetback(10.0, 64.0, 10.0, new Vec3(10.0, 64.0, 30.0), absolute), "a respawn or portal hop");
   }

   @Test
   void relativeMovesAreMeasuredAsOffsets() {
      Set<Relative> relative = EnumSet.of(Relative.X, Relative.Y, Relative.Z);
      assertTrue(FlagCheck.isSetback(1000.0, 64.0, 1000.0, new Vec3(-0.5, 0.0, 0.0), relative), "half a block back");
      assertFalse(FlagCheck.isSetback(1000.0, 64.0, 1000.0, new Vec3(20.0, 0.0, 0.0), relative), "twenty blocks is a teleport");
      assertFalse(FlagCheck.isSetback(1000.0, 64.0, 1000.0, Vec3.ZERO, relative), "rotation only: nothing moved back");
   }
}
