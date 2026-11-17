package gemini.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotesTextTest {
   private static final String NOTES = "Base at 120 64 -300\n[ ] get iron\n- [x] build farm\n  - [ ] find a village\n\n[X] mine diamonds\n[ ]";

   @Test
   void parsesTodosInTheUsualSpellings() {
      assertTrue(NotesText.parse("[ ] a").todo());
      assertFalse(NotesText.parse("[ ] a").done());
      assertTrue(NotesText.parse("- [x] a").done());
      assertTrue(NotesText.parse("* [X] a").done());
      assertEquals("a", NotesText.parse("  [ ]   a  ").text());
      assertFalse(NotesText.parse("[a] not a todo").todo());
      assertFalse(NotesText.parse("plain").todo());
   }

   @Test
   void hudListsOpenTodosOrAllLines() {
      assertEquals(List.of("[ ] get iron", "[ ] find a village", "[ ] "), NotesText.hudLines(NOTES, true, 5, 40));
      assertEquals(List.of("Base at 120 64 -300", "[ ] get iron", "+4 more"), NotesText.hudLines(NOTES, false, 2, 40));
      assertEquals(List.of("[ ] get iron", "+2 more"), NotesText.hudLines(NOTES, true, 1, 40));
      assertEquals(List.of(), NotesText.hudLines("", true, 5, 40));
      assertEquals(List.of(), NotesText.hudLines(null, false, 5, 40));
      assertEquals(List.of("Base at..."), NotesText.hudLines("Base at 120 64 -300", false, 5, 10));
   }

   @Test
   void cutShortensWithAnEllipsis() {
      assertEquals("hello", NotesText.cut("hello", 5));
      assertEquals("hell...", NotesText.cut("hello world", 7));
      assertEquals("he", NotesText.cut("hello", 2));
   }

   @Test
   void appendAddsALine() {
      assertEquals("a", NotesText.append("", "a"));
      assertEquals("a\nb", NotesText.append("a", " b "));
      assertEquals("a\nb", NotesText.append("a\n", "b"));
      assertEquals("a", NotesText.append("a", "   "));
   }

   @Test
   void markDoneCountsOpenTodosOnly() {
      String done = NotesText.markDone(NOTES, 2);
      assertEquals(NOTES.replace("  - [ ] find a village", "  - [x] find a village"), done);
      assertEquals(2, NotesText.openTodos(done));
      assertEquals(3, NotesText.openTodos(NOTES));
      assertNull(NotesText.markDone(NOTES, 4));
      assertNull(NotesText.markDone(NOTES, 0));
      assertNull(NotesText.markDone(null, 1));
      assertEquals("a\r\n[x] b\r\n", NotesText.markDone("a\r\n[ ] b\r\n", 1), "Windows line endings are kept");
   }
}
