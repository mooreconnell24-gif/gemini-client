package gemini.ui.help;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Word wrapping for text that may carry Minecraft formatting codes ({@code §x}). Lines break at spaces; a word longer
 * than the line is broken by characters. A colour code that is still active at the end of a line is repeated at the
 * start of the next, so a bold phrase that wraps stays bold. {@code width} measures a string the way the font does
 * (formatting codes count as zero width). Pure, so it is unit tested.
 */
public final class TextWrap {
   private TextWrap() {
   }

   public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isEmpty()) {
         lines.add("");
         return lines;
      }

      int limit = Math.max(1, maxWidth);
      StringBuilder line = new StringBuilder();
      String carry = "";
      for (String word : text.split(" ", -1)) {
         if (word.isEmpty()) {
            continue;
         }

         String candidate = line.isEmpty() ? word : line + " " + word;
         if (width.applyAsInt(candidate) <= limit) {
            line.setLength(0);
            line.append(candidate);
            continue;
         }

         if (!line.isEmpty()) {
            String done = line.toString();
            lines.add(done);
            carry = activeCode(carry + done);
            line.setLength(0);
         }

         String piece = carry + word;
         // A single word wider than the line: cut it by characters, never inside a formatting code.
         while (width.applyAsInt(piece) > limit) {
            int cut = cutPoint(piece, limit, width);
            String head = piece.substring(0, cut);
            lines.add(head);
            carry = activeCode(head);
            piece = carry + piece.substring(cut);
            if (cut <= carry.length()) {
               // Nothing fits (a limit narrower than one character): give up on cutting rather than loop.
               break;
            }
         }

         line.append(piece);
      }

      if (!line.isEmpty() || lines.isEmpty()) {
         lines.add(line.toString());
      }

      return lines;
   }

   /** The formatting code in effect at the end of {@code text} ("" after a reset or when there is none). */
   static String activeCode(String text) {
      String code = "";
      for (int i = 0; i + 1 < text.length(); i++) {
         if (text.charAt(i) == '§') {
            char c = Character.toLowerCase(text.charAt(i + 1));
            code = c == 'r' ? "" : "§" + text.charAt(i + 1);
            i++;
         }
      }

      return code;
   }

   private static int cutPoint(String piece, int limit, ToIntFunction<String> width) {
      int best = 0;
      for (int i = 1; i <= piece.length(); i++) {
         if (piece.charAt(i - 1) == '§') {
            // A cut right after "§" would split the code from its letter.
            continue;
         }

         if (width.applyAsInt(piece.substring(0, i)) > limit) {
            break;
         }

         best = i;
      }

      return Math.max(1, best);
   }
}
