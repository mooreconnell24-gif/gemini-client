package gemini.ui.clickgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingDisclosureTest {
   @Test
   void shortListsAreNeverFolded() {
      for (int total = 0; total <= SettingDisclosure.THRESHOLD; total++) {
         assertFalse(SettingDisclosure.foldable(total));
         assertEquals(total, SettingDisclosure.shown(total, false, false));
         assertEquals(0, SettingDisclosure.hidden(total, false, false));
      }
   }

   @Test
   void longListsShowTheFirstFewUntilUnfolded() {
      int total = 17;
      assertTrue(SettingDisclosure.foldable(total));
      assertEquals(SettingDisclosure.SHOWN, SettingDisclosure.shown(total, false, false));
      assertEquals(total - SettingDisclosure.SHOWN, SettingDisclosure.hidden(total, false, false));
      assertEquals(total, SettingDisclosure.shown(total, true, false), "unfolded shows everything");
   }

   @Test
   void searchingShowsEverySetting() {
      assertEquals(30, SettingDisclosure.shown(30, false, true));
      assertEquals(0, SettingDisclosure.hidden(30, false, true));
   }

   @Test
   void aSettingPastTheFoldNeedsUnfolding() {
      assertFalse(SettingDisclosure.needsUnfold(SettingDisclosure.SHOWN - 1, 20));
      assertTrue(SettingDisclosure.needsUnfold(SettingDisclosure.SHOWN, 20));
      assertFalse(SettingDisclosure.needsUnfold(7, SettingDisclosure.THRESHOLD), "short lists are never folded");
   }

   @Test
   void foldingAlwaysHidesSomething() {
      // A list one over the threshold must hide at least one setting, or the More row would be pointless.
      assertTrue(SettingDisclosure.hidden(SettingDisclosure.THRESHOLD + 1, false, false) > 0);
   }
}
