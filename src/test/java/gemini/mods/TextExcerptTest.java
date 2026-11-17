package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextExcerptTest {
   @Test
   void modMenuDescriptionBecomesPlainText() {
      String body = Modrinth.parseProject(Fixtures.modrinth("project_modmenu.json")).body();
      List<String> paragraphs = TextExcerpt.paragraphs(body, 400);
      assertFalse(paragraphs.isEmpty());
      assertEquals("Mod Menu", paragraphs.get(0));
      String all = String.join(" ", paragraphs);
      assertTrue(all.contains("Mod Menu lets you view the mods you have installed"), all);
      assertFalse(all.contains("!["));
      assertFalse(all.contains("https://"));
      assertFalse(all.contains("#"));
      assertTrue(all.length() <= 404);
   }

   @Test
   void stripsMarkupKeepsLinkText() {
      String md = "## Features\n\n- **Fast** and [small](https://example.com)\n<p align=\"center\"><img src=\"x\"></p>\n\n| a | b |\n|---|---|\n\n```\ncode\n```\nEnd.";
      assertEquals(List.of("Features", "Fast and small", "End."), TextExcerpt.paragraphs(md, 500));
   }

   @Test
   void dropsEmojiTheFontCannotDraw() {
      assertEquals(List.of("✔ Fast", "Works"), TextExcerpt.paragraphs("✔️ Fast\n\n🚀 Works", 100));
   }

   @Test
   void cutsAtAWord() {
      List<String> out = TextExcerpt.paragraphs("one two three four five six seven", 14);
      assertEquals(List.of("one two three..."), out);
      assertTrue(TextExcerpt.paragraphs(null, 100).isEmpty());
   }
}
