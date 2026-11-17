package gemini.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure text rules of world notes. Notes are plain text; a line that starts with {@code [ ]} (or {@code - [ ]}) is
 * an open to-do and one that starts with {@code [x]} is a done one, so the file reads well in any editor.
 */
public final class NotesText {
   public static final int MAX_CHARS = 10_000;
   private static final String ELLIPSIS = "...";

   private NotesText() {
   }

   /** One line as the HUD shows it: the text without the to-do marker, and whether it is a to-do and done. */
   public record Line(String text, boolean todo, boolean done) {
   }

   /** Parses one line. */
   public static NotesText.Line parse(String raw) {
      String line = raw.strip();
      String body = line.startsWith("- ") || line.startsWith("* ") ? line.substring(2).stripLeading() : line;
      if (body.length() >= 3 && body.charAt(0) == '[' && body.charAt(2) == ']') {
         char mark = body.charAt(1);
         if (mark == ' ') {
            return new NotesText.Line(body.substring(3).strip(), true, false);
         } else if (mark == 'x' || mark == 'X') {
            return new NotesText.Line(body.substring(3).strip(), true, true);
         }
      }

      return new NotesText.Line(line, false, false);
   }

   /**
    * The lines to show on the HUD: with {@code todosOnly}, the open to-dos; otherwise every non-blank line. At most
    * {@code max} entries, each cut to {@code maxChars} with "..." at the end; one more line says how many were left out.
    */
   public static List<String> hudLines(String text, boolean todosOnly, int max, int maxChars) {
      List<String> shown = new ArrayList<>();
      if (text == null || text.isEmpty() || max <= 0) {
         return shown;
      }

      int hidden = 0;
      for (String raw : text.split("\\R")) {
         if (raw.isBlank()) {
            continue;
         }

         NotesText.Line line = parse(raw);
         if (todosOnly && (!line.todo() || line.done())) {
            continue;
         }

         if (shown.size() >= max) {
            hidden++;
            continue;
         }

         String body = line.todo() ? (line.done() ? "[x] " : "[ ] ") + line.text() : line.text();
         shown.add(cut(body, maxChars));
      }

      if (hidden > 0) {
         shown.add("+" + hidden + " more");
      }

      return shown;
   }

   /** {@code text} shortened to {@code maxChars} characters, ending in "..." when cut. */
   public static String cut(String text, int maxChars) {
      if (text.length() <= maxChars || maxChars <= ELLIPSIS.length()) {
         return text.length() <= maxChars ? text : text.substring(0, Math.max(0, maxChars));
      }

      return text.substring(0, maxChars - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
   }

   /** {@code text} with {@code line} added as a new last line. */
   public static String append(String text, String line) {
      String base = text == null ? "" : text;
      String add = line == null ? "" : line.strip();
      if (add.isEmpty()) {
         return base;
      }

      return base.isEmpty() ? add : (base.endsWith("\n") ? base : base + "\n") + add;
   }

   /**
    * {@code text} with its {@code n}-th open to-do (1-based, counted as the HUD lists them) marked done, or null when
    * there is no such to-do. The rest of the text is kept exactly as it was.
    */
   public static String markDone(String text, int n) {
      if (text == null || n < 1) {
         return null;
      }

      String[] lines = text.split("\n", -1);
      int open = 0;
      for (int i = 0; i < lines.length; i++) {
         NotesText.Line line = parse(lines[i]);
         if (line.todo() && !line.done() && ++open == n) {
            int at = lines[i].indexOf("[ ]");
            lines[i] = lines[i].substring(0, at) + "[x]" + lines[i].substring(at + 3);
            return String.join("\n", lines);
         }
      }

      return null;
   }

   /** How many open to-dos the text has. */
   public static int openTodos(String text) {
      if (text == null || text.isEmpty()) {
         return 0;
      }

      int open = 0;
      for (String raw : text.split("\\R")) {
         NotesText.Line line = parse(raw);
         if (line.todo() && !line.done()) {
            open++;
         }
      }

      return open;
   }
}
