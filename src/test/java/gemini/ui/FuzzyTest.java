package gemini.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FuzzyTest {
   @Test
   void scatteredLettersMatchInOrder() {
      assertTrue(Fuzzy.score("kaura", "KillAura") > 0);
      assertTrue(Fuzzy.score("nofal", "NoFall") > 0);
      assertEquals(0, Fuzzy.score("xyz", "KillAura"));
      assertEquals(0, Fuzzy.score("aruak", "KillAura"));
   }

   @Test
   void prefixBeatsScatteredMatch() {
      assertTrue(Fuzzy.score("kill", "KillAura") > Fuzzy.score("kill", "AutoKiller Light"));
      assertTrue(Fuzzy.score("aura", "KillAura") > Fuzzy.score("aura", "Auto Bucket Use Rate"));
   }

   @Test
   void everyWordMustMatch() {
      assertTrue(Fuzzy.score("kill aura", "KillAura") > 0);
      assertEquals(0, Fuzzy.score("kill fly", "KillAura"));
   }

   @Test
   void blankQueryMatchesEverything() {
      assertEquals(1, Fuzzy.score("  ", "Anything"));
      assertTrue(Fuzzy.match("", "Flight", List.of(), "Fly", List.of()).matched());
   }

   @Test
   void nameMatchReportsNoSetting() {
      Fuzzy.Match match = Fuzzy.match("flight", "Flight", List.of(), "Lets you fly", List.of("Speed", "Flight Mode"));
      assertTrue(match.matched());
      assertNull(match.setting());
   }

   @Test
   void settingNameMatchIsReported() {
      Fuzzy.Match match = Fuzzy.match("cps", "AutoClicker", List.of(), "Clicks for you", List.of("Mode", "Max CPS", "Min CPS"));
      assertTrue(match.matched());
      assertEquals("Max CPS", match.setting());
   }

   @Test
   void settingNamesNeedTheWholeWord() {
      // scattered letters inside a setting name are noise, not a match
      Fuzzy.Match match = Fuzzy.match("rng", "Scaffold", List.of(), "Places blocks", List.of("Rotation Speed", "Range Limit"));
      assertFalse(match.matched());
   }

   @Test
   void aliasCountsLikeTheName() {
      Fuzzy.Match match = Fuzzy.match("aimbot", "AimAssist", List.of("Aimbot"), "Helps aim", List.of());
      assertTrue(match.score() >= Fuzzy.score("aimbot", "Aimbot"));
      assertNull(match.setting());
   }

   @Test
   void descriptionMatchesButWeighsLess() {
      Fuzzy.Match description = Fuzzy.match("lava", "Jesus", List.of(), "Walk on water and lava", List.of());
      assertTrue(description.matched());
      assertTrue(description.score() < Fuzzy.score("lava", "Lava"));
   }
}
