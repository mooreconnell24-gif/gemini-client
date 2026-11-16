package gemini.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandHelpTest {
   @Test
   void everyDispatcherWordIsDocumented() {
      StringBuilder all = new StringBuilder();
      for (CommandHelp.Entry entry : CommandHelp.ENTRIES) {
         all.append(' ').append(entry.usage()).append(' ').append(entry.description());
      }

      String text = all.toString();
      for (String word : CommandHelp.WORDS) {
         if (word.equals("baritone")) {
            continue; // documented as ".b <command>" and "#<command>"
         }

         assertTrue(text.contains("." + word), "." + word + " is in the help");
      }
   }

   @Test
   void entriesRunKnownCommands() {
      Set<String> words = new HashSet<>(CommandHelp.WORDS);
      for (CommandHelp.Entry entry : CommandHelp.ENTRIES) {
         assertTrue(words.contains(entry.word()), entry.word() + " is dispatched");
         assertFalse(entry.description().isBlank());
      }
   }

   @Test
   void everyGroupHasCommands() {
      for (CommandHelp.Group group : CommandHelp.Group.values()) {
         assertFalse(CommandHelp.of(group).isEmpty(), group.label);
      }
   }

   @Test
   void chatHelpListsEveryEntry() {
      List<String> lines = CommandHelp.chatLines();
      assertEquals(1 + CommandHelp.Group.values().length + CommandHelp.ENTRIES.size() + 1, lines.size());
   }

   @Test
   void prefillStopsBeforeArguments() {
      assertEquals(".bind ", CommandHelp.prefill(".bind <module> <key>"));
      assertEquals(".panic", CommandHelp.prefill(".panic"));
      assertEquals(".config ", CommandHelp.prefill(".config save|load|delete|list|reset <name>"));
      assertEquals(".config share ", CommandHelp.prefill(".config share [name]"));
      assertEquals(".config import code ", CommandHelp.prefill(".config import code [<code>|clipboard] [name]"));
      assertEquals("#", CommandHelp.prefill("#<command> or .b <command>"));
      assertEquals(".day", CommandHelp.prefill(".day / .noon / .night"));
      assertEquals(".gm ", CommandHelp.prefill(".gm survival|creative|adventure|spectator"));
      assertEquals(".help ", CommandHelp.prefill(".help [chat]"));
   }
}
