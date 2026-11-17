package gemini.ui.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.ui.Theme;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteTest {
   private static PaletteRanking.Candidate module(String name, String description, String... settings) {
      return new PaletteRanking.Candidate(PaletteRanking.Kind.MODULE, "module:" + name, name, List.of(), description, List.of(settings), name);
   }

   private static PaletteRanking.Candidate page(String name, String... keywords) {
      return new PaletteRanking.Candidate(PaletteRanking.Kind.PAGE, "page:" + name, name, List.of(keywords), "a page", null, name);
   }

   private static PaletteRanking.Candidate command(String usage, String word) {
      return new PaletteRanking.Candidate(PaletteRanking.Kind.COMMAND, "cmd:" + usage, usage, List.of(word), "a command", null, usage);
   }

   private static final List<PaletteRanking.Candidate> ALL = List.of(
      module("KillAura", "Attacks entities around you", "Range", "Rotate"),
      module("AutoClicker", "Clicks for you", "CPS", "Jitter"),
      module("Flight", "Lets you fly", "Speed"),
      module("NoFall", "No fall damage", "Mode"),
      page("Keybinds", "keys", "binds", "conflicts"),
      page("Presets & Configs", "profiles", "share"),
      command(".bind <module> <key>", "bind"),
      command(".config share [name]", "config")
   );

   private static List<String> names(List<PaletteRanking.Result> results) {
      return results.stream().map(r -> r.candidate().name()).toList();
   }

   @Test
   void fuzzyNameMatchesComeFirst() {
      List<PaletteRanking.Result> results = PaletteRanking.rank("kaura", ALL, PaletteRanking.Kind.ALL, List.of(), 10);
      assertEquals("KillAura", results.get(0).candidate().name());
      assertNull(results.get(0).setting());
   }

   @Test
   void settingNamesFindTheirModuleAndSayWhich() {
      List<PaletteRanking.Result> results = PaletteRanking.rank("cps", ALL, PaletteRanking.Kind.ALL, List.of(), 10);
      assertEquals("AutoClicker", results.get(0).candidate().name());
      assertEquals("CPS", results.get(0).setting());
   }

   @Test
   void pagesAndCommandsAreSearchable() {
      assertEquals("Keybinds", PaletteRanking.rank("conflicts", ALL, PaletteRanking.Kind.ALL, List.of(), 10).get(0).candidate().name());
      assertEquals(".bind <module> <key>", PaletteRanking.rank("bind", ALL, PaletteRanking.Kind.COMMAND, List.of(), 10).get(0).candidate().name());
      assertEquals("Presets & Configs", PaletteRanking.rank("share", ALL, PaletteRanking.Kind.PAGE, List.of(), 10).get(0).candidate().name());
   }

   @Test
   void filterKeepsOneKind() {
      for (PaletteRanking.Kind kind : List.of(PaletteRanking.Kind.MODULE, PaletteRanking.Kind.PAGE, PaletteRanking.Kind.COMMAND)) {
         for (PaletteRanking.Result r : PaletteRanking.rank("", ALL, kind, List.of(), 50)) {
            assertEquals(kind, r.candidate().kind());
         }
      }

      assertEquals(ALL.size(), PaletteRanking.rank("", ALL, PaletteRanking.Kind.ALL, List.of(), 50).size());
      assertTrue(PaletteRanking.rank("zzzzqq", ALL, PaletteRanking.Kind.ALL, List.of(), 50).isEmpty());
   }

   @Test
   void tabCyclesKindsBothWays() {
      PaletteRanking.Kind kind = PaletteRanking.Kind.ALL;
      for (int i = 0; i < PaletteRanking.Kind.values().length; i++) {
         kind = kind.cycle(1);
      }

      assertEquals(PaletteRanking.Kind.ALL, kind);
      assertEquals(PaletteRanking.Kind.COMMAND, PaletteRanking.Kind.ALL.cycle(-1));
   }

   @Test
   void emptyQueryListsRecentFirstThenByKindAndName() {
      List<String> recent = List.of("module:NoFall", "page:Keybinds");
      List<String> order = names(PaletteRanking.rank("", ALL, PaletteRanking.Kind.ALL, recent, 50));
      assertEquals("NoFall", order.get(0));
      assertEquals("Keybinds", order.get(1));
      assertEquals(List.of("AutoClicker", "Flight", "KillAura"), order.subList(2, 5), "then modules by name");
   }

   @Test
   void recentPicksBreakCloseCalls() {
      List<PaletteRanking.Candidate> twins = List.of(module("Speed", "x"), module("Speedmine", "x"));
      assertEquals("Speed", PaletteRanking.rank("speed", twins, PaletteRanking.Kind.ALL, List.of(), 5).get(0).candidate().name());
      assertEquals("Speedmine", PaletteRanking.rank("speed", twins, PaletteRanking.Kind.ALL, List.of("module:Speedmine"), 5).get(0).candidate().name());
   }

   @Test
   void limitAndRemember() {
      assertEquals(2, PaletteRanking.rank("", ALL, PaletteRanking.Kind.ALL, List.of(), 2).size());
      List<String> recent = PaletteRanking.remember(List.of("a", "b", "c"), "b", 3);
      assertEquals(List.of("b", "a", "c"), recent);
      assertEquals(List.of("d", "a"), PaletteRanking.remember(List.of("a", "b", "c"), "d", 2));
   }

   @Test
   void layoutHoldsAtEveryGuiScale() {
      int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {1280, 720}, {427, 240}, {320, 240}, {320, 180}};
      for (int[] size : sizes) {
         PaletteLayout l = PaletteLayout.compute(size[0], size[1]);
         String at = size[0] + "x" + size[1];
         assertTrue(l.x() >= Theme.EDGE - 1 && l.x() + l.width() <= size[0] - Theme.EDGE + 1, "inside horizontally at " + at);
         assertTrue(l.y() >= 0 && l.y() + l.height() <= size[1], "inside vertically at " + at);
         assertTrue(l.rows() >= PaletteLayout.MIN_ROWS && l.rows() <= PaletteLayout.MAX_ROWS, "rows at " + at);
         assertTrue(l.kindsY() >= l.fieldY() + PaletteLayout.FIELD_HEIGHT, "kinds below the field at " + at);
         assertTrue(l.listY() >= l.kindsY() + PaletteLayout.KINDS_HEIGHT, "list below the kinds at " + at);
         assertTrue(l.footerY() >= l.listY() + l.rows() * PaletteLayout.ROW_HEIGHT, "footer below the list at " + at);
         assertTrue(l.footerY() + PaletteLayout.FOOTER_HEIGHT <= l.y() + l.height(), "footer inside the card at " + at);
      }

      assertEquals(PaletteLayout.MAX_ROWS, PaletteLayout.compute(1920, 1080).rows());
      assertEquals(PaletteLayout.MAX_WIDTH, PaletteLayout.compute(960, 540).width());
   }
}
