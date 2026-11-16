package gemini.ui.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** Changelog parsing, wrapping and the help screen's layout maths. */
class HelpTest {
   /** A monospaced stand-in for the font: 6 px per visible character, formatting codes are free. */
   private static final ToIntFunction<String> WIDTH = s -> Markdown.strip(s).length() * 6;

   private static String bundledChangelog() throws IOException {
      try (InputStream in = HelpTest.class.getResourceAsStream("/assets/gemini/changelog.md")) {
         assertNotNull(in, "changelog.md is bundled");
         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   // ---- markdown -------------------------------------------------------------------------------------------

   @Test
   void parsesHeadingsParagraphsBulletsTablesAndRules() {
      String source = """
         # Gemini 3.3.0

         **A new look.** Every surface
         shares one theme.

         - **Home screen.** Buttons
           continued here
           - nested `code`
         * star bullet

         | Module | What it does |
         |---|:---:|
         | Auto City | Mines \\| things |
         | Short |
         ---
         <details>
         <summary>Old</summary>
         Read [the docs](https://example.com/x).
         </details>
         """;
      List<Markdown.Block> blocks = Markdown.parse(source);
      assertEquals(new Markdown.Heading(1, "Gemini 3.3.0"), blocks.get(0));
      assertEquals(new Markdown.Paragraph(Markdown.BOLD + "A new look." + Markdown.RESET + " Every surface shares one theme."), blocks.get(1));
      assertEquals(new Markdown.Bullet(0, Markdown.BOLD + "Home screen." + Markdown.RESET + " Buttons continued here"), blocks.get(2));
      assertEquals(new Markdown.Bullet(1, "nested " + Markdown.CODE + "code" + Markdown.RESET), blocks.get(3));
      assertEquals(new Markdown.Bullet(0, "star bullet"), blocks.get(4));
      Markdown.Table table = assertInstanceOf(Markdown.Table.class, blocks.get(5));
      assertEquals(List.of("Module", "What it does"), table.header());
      assertEquals(List.of("Auto City", "Mines | things"), table.rows().get(0));
      assertEquals(List.of("Short", ""), table.rows().get(1), "short rows are padded to the header");
      assertInstanceOf(Markdown.Rule.class, blocks.get(6));
      // HTML tags vanish; the summary text stays as a paragraph with the link spelled out.
      assertEquals(new Markdown.Paragraph("Old Read the docs (https://example.com/x)."), blocks.get(7));
      assertEquals(8, blocks.size());
   }

   @Test
   void italicDoesNotEatBulletsOrPlaceholders() {
      assertEquals("Fast Fall's Ledges mode", Markdown.inline("Fast Fall's *Ledges* mode"));
      assertEquals("2 * 3 * 4", Markdown.inline("2 * 3 * 4"));
      assertEquals("#mine <block> [block...]", Markdown.inline("#mine <block> [block...]"));
   }

   @Test
   void bundledChangelogParses() throws IOException {
      List<Markdown.Block> blocks = Markdown.parse(bundledChangelog());
      assertEquals("3.3.0", Markdown.firstVersion(blocks));
      long tables = blocks.stream().filter(b -> b instanceof Markdown.Table).count();
      assertTrue(tables >= 1, "the new-modules table");
      Markdown.Table modules = (Markdown.Table)blocks.stream().filter(b -> b instanceof Markdown.Table).findFirst().orElseThrow();
      assertEquals(15, modules.rows().size());
      assertTrue(blocks.stream().filter(b -> b instanceof Markdown.Bullet).count() > 10);
      for (Markdown.Block block : blocks) {
         String text = block instanceof Markdown.Paragraph p ? p.text() : (block instanceof Markdown.Bullet b ? b.text() : "");
         assertFalse(text.contains("**"), "no raw bold markers left: " + text);
         assertFalse(text.contains("`"), "no raw code markers left: " + text);
      }
   }

   // ---- wrapping -------------------------------------------------------------------------------------------

   @Test
   void wrapsAtSpacesWithinTheWidth() {
      List<String> lines = TextWrap.wrap("the quick brown fox jumps over the lazy dog", 60, WIDTH);
      assertEquals(List.of("the quick", "brown fox", "jumps over", "the lazy", "dog"), lines);
      for (String line : lines) {
         assertTrue(WIDTH.applyAsInt(line) <= 60);
      }
   }

   @Test
   void carriesFormattingAcrossLines() {
      String text = Markdown.BOLD + "bold words keep going" + Markdown.RESET + " plain";
      List<String> lines = TextWrap.wrap(text, 60, WIDTH);
      assertEquals(Markdown.BOLD + "bold words", lines.get(0));
      assertEquals(Markdown.BOLD + "keep going" + Markdown.RESET, lines.get(1));
      assertEquals("plain", lines.get(2), "a reset ends the carry");
      assertEquals("", TextWrap.activeCode("a§fb§rc"));
      assertEquals("§b", TextWrap.activeCode("x§fy§bz"));
   }

   @Test
   void breaksWordsLongerThanTheLine() {
      List<String> lines = TextWrap.wrap("abcdefghijklmnop", 30, WIDTH);
      assertEquals(List.of("abcde", "fghij", "klmno", "p"), lines);
      List<String> coded = TextWrap.wrap("§babcdefgh", 30, WIDTH);
      assertEquals("§babcde", coded.get(0));
      assertEquals("§bfgh", coded.get(1), "the colour continues on the broken part");
      assertEquals(List.of(""), TextWrap.wrap("", 30, WIDTH));
   }

   // ---- document layout ------------------------------------------------------------------------------------

   @Test
   void nothingIsWiderThanItsColumn() throws IOException {
      List<Markdown.Block> blocks = Markdown.parse(bundledChangelog());
      for (int width : new int[]{120, 200, 300, 444}) {
         DocLayout.Result result = DocLayout.layout(blocks, width, WIDTH);
         int lastY = -1;
         for (DocLayout.Piece piece : result.pieces()) {
            int visible = piece.kind() == DocLayout.Kind.H1 ? Math.round(piece.width() * DocLayout.H1_SCALE) : piece.width();
            assertTrue(piece.x() >= 0, "x inside at " + width);
            assertTrue(piece.x() + visible <= width, piece.kind() + " \"" + piece.text() + "\" overflows " + width);
            assertTrue(piece.y() >= 0 && piece.y() <= result.height(), "y inside the document");
            if (piece.kind() == DocLayout.Kind.H1 || piece.kind() == DocLayout.Kind.H2) {
               assertTrue(piece.y() > lastY, "headings go down the page");
               lastY = piece.y();
            }
         }

         assertTrue(result.height() > 0);
      }
   }

   @Test
   void narrowerColumnsAreTaller() throws IOException {
      List<Markdown.Block> blocks = Markdown.parse(bundledChangelog());
      assertTrue(DocLayout.layout(blocks, 160, WIDTH).height() > DocLayout.layout(blocks, 420, WIDTH).height());
   }

   @Test
   void twoColumnTableRowsDoNotOverlap() {
      Markdown.Table table = new Markdown.Table(List.of("Input", "Action"), List.of(
         List.of("A rather long input description", "short"),
         List.of("x", "a much longer action description that will wrap onto more lines than one")
      ));
      DocLayout.Result result = DocLayout.layout(List.of(table), 200, WIDTH);
      int keyRight = 0;
      int valueLeft = Integer.MAX_VALUE;
      for (DocLayout.Piece piece : result.pieces()) {
         if (piece.kind() == DocLayout.Kind.CELL_KEY) {
            keyRight = Math.max(keyRight, piece.x() + piece.width());
         } else if (piece.kind() == DocLayout.Kind.CELL) {
            valueLeft = Math.min(valueLeft, piece.x());
         }
      }

      assertTrue(keyRight < valueLeft, "the key column ends before the value column starts");
   }

   // ---- what's new and the layout --------------------------------------------------------------------------

   @Test
   void whatsNewOpensOncePerVersion() {
      assertTrue(HelpContent.shouldShowWhatsNew(null, "3.3.0"), "first start");
      assertTrue(HelpContent.shouldShowWhatsNew("3.2.0", "3.3.0"), "after an update");
      assertFalse(HelpContent.shouldShowWhatsNew("3.3.0", "3.3.0"), "already seen");
      assertFalse(HelpContent.shouldShowWhatsNew("3.3.0", null));
      assertFalse(HelpContent.shouldShowWhatsNew("3.3.0", " "));
   }

   @Test
   void controlsAndAboutHaveContent() {
      List<HelpContent.Section> sections = HelpContent.controls("Right Shift", "Ctrl+K");
      assertEquals(5, sections.size());
      assertTrue(sections.get(0).controls().get(0).input().equals("Right Shift"));
      assertTrue(HelpContent.about("3.3.0", "26.3").get(0).contains("3.3.0"));
   }

   @Test
   void layoutHoldsAtEveryGuiScale() {
      int[] full = {60, 48, 54, 30};
      int[] brief = {18, 24, 24, 30};
      int[][] sizes = {{1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {1280, 720}, {427, 240}, {320, 240}, {320, 180}, {200, 150}};
      for (int[] size : sizes) {
         HelpLayout l = HelpLayout.compute(size[0], size[1], full, brief);
         String at = size[0] + "x" + size[1];
         assertTrue(l.panelX() >= 0 && l.panelX() + l.panelWidth() <= size[0], "panel inside horizontally at " + at);
         assertTrue(l.panelY() >= 0 && l.panelY() + l.panelHeight() <= size[1], "panel inside vertically at " + at);
         assertTrue(l.panelWidth() <= HelpLayout.MAX_WIDTH);
         int last = l.tabX().length - 1;
         assertTrue(l.tabX()[last] + l.tabWidth()[last] <= l.panelX() + l.panelWidth() - 4, "tabs fit at " + at);
         for (int i = 0; i < last; i++) {
            assertTrue(l.tabX()[i] + l.tabWidth()[i] <= l.tabX()[i + 1], "tabs do not overlap at " + at);
         }

         assertTrue(l.contentY() >= l.tabsY() + HelpLayout.TAB_HEIGHT, "content below the tabs at " + at);
         assertTrue(l.contentY() + l.contentHeight() <= l.footerY(), "content above the footer at " + at);
         assertTrue(l.footerY() + HelpLayout.FOOTER_HEIGHT <= l.panelY() + l.panelHeight(), "footer inside at " + at);
         assertTrue(l.contentHeight() >= 40, "some content shows at " + at);
      }

      assertFalse(HelpLayout.compute(960, 540, full, brief).shortTabs());
      assertTrue(HelpLayout.compute(200, 150, new int[]{80, 80, 80, 80}, brief).shortTabs());
   }
}
