package gemini.module.modules.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class CityPlannerTest {
   @Test
   void emptyListHasNoBest() {
      assertNull(CityPlanner.best(List.of()));
   }

   @Test
   void crystalSpotBeatsFasterAndCloser() {
      CityPlanner.Candidate quick = new CityPlanner.Candidate(0, false, 10, 1.0);
      CityPlanner.Candidate crystal = new CityPlanner.Candidate(1, true, 200, 9.0);
      assertEquals(1, CityPlanner.best(List.of(quick, crystal)).id());
   }

   @Test
   void quickerBreakBeatsCloserWhenCrystalSpotsTie() {
      CityPlanner.Candidate obsidian = new CityPlanner.Candidate(0, true, 190, 1.0);
      CityPlanner.Candidate enderChest = new CityPlanner.Candidate(1, true, 45, 6.0);
      assertEquals(1, CityPlanner.best(List.of(obsidian, enderChest)).id());
   }

   @Test
   void closerWinsWhenBreakTimesAreNearlyEqual() {
      CityPlanner.Candidate far = new CityPlanner.Candidate(0, true, 190, 12.0);
      CityPlanner.Candidate near = new CityPlanner.Candidate(1, true, 192, 2.0);
      assertEquals(1, CityPlanner.best(List.of(far, near)).id());
   }
}
