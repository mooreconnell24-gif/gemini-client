package gemini.ui.font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Fitting text into a width: cutting with an ellipsis, and word wrapping. Works on any width function, so it is
 * the same for the smooth font and the Minecraft font, and testable without the game.
 */
public final class TextFit {
   /** The ellipsis of smooth text. */
   public static final String ELLIPSIS = "…";
   /** The ellipsis of Minecraft-font text (the pixel font's own ellipsis glyph is wide and sits low). */
   public static final String DOTS = "..";

   private TextFit() {
   }

   /**
    * {@code text} cut down and ended with {@code ellipsis} so that it is no wider than {@code maxWidth}. Returns the
    * text itself when it fits, and an empty string when not even the ellipsis does.
    */
   public static String ellipsize(String text, int maxWidth, ToIntFunction<String> width, String ellipsis) {
      if (text == null || text.isEmpty() || maxWidth <= 0) {
         return "";
      }

      if (width.applyAsInt(text) <= maxWidth) {
         return text;
      }

      if (width.applyAsInt(ellipsis) > maxWidth) {
         return "";
      }

      // Binary search on the prefix length: widths only grow as characters are added.
      int lo = 0;
      int hi = text.length() - 1;
      while (lo < hi) {
         int mid = (lo + hi + 1) >>> 1;
         if (width.applyAsInt(cut(text, mid) + ellipsis) <= maxWidth) {
            lo = mid;
         } else {
            hi = mid - 1;
         }
      }

      return cut(text, lo) + ellipsis;
   }

   /** The first {@code length} chars without trailing spaces and without half of a surrogate pair. */
   private static String cut(String text, int length) {
      int end = length;
      if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
         end--;
      }

      return text.substring(0, end).stripTrailing();
   }

   /**
    * Breaks {@code text} into lines no wider than {@code maxWidth}, at spaces where possible and inside a word only
    * when the word alone is too wide. Explicit {@code \n} always breaks. At most {@code maxLines} lines are returned
    * (0 = no limit); when text is left over the last line ends with {@code ellipsis}.
    */
   public static List<String> wrap(String text, int maxWidth, int maxLines, ToIntFunction<String> width, String ellipsis) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isEmpty() || maxWidth <= 0) {
         return lines;
      }

      boolean truncated = false;
      outer:
      for (String paragraph : text.split("\n", -1)) {
         String rest = paragraph.strip();
         if (rest.isEmpty()) {
            if (full(lines, maxLines)) {
               truncated = true;
               break;
            }

            lines.add("");
            continue;
         }

         while (!rest.isEmpty()) {
            if (full(lines, maxLines)) {
               truncated = true;
               break outer;
            }

            if (width.applyAsInt(rest) <= maxWidth) {
               lines.add(rest);
               break;
            }

            int breakAt = lastFittingBreak(rest, maxWidth, width);
            lines.add(rest.substring(0, breakAt).stripTrailing());
            rest = rest.substring(breakAt).stripLeading();
         }
      }

      if (truncated && !lines.isEmpty()) {
         int last = lines.size() - 1;
         // Exactly one ellipsis: shorten the line until it fits with it.
         String line = lines.get(last).stripTrailing();
         while (!line.isEmpty() && width.applyAsInt(line + ellipsis) > maxWidth) {
            line = line.substring(0, line.length() - 1).stripTrailing();
         }

         lines.set(last, line + ellipsis);
      }

      return lines;
   }

   private static boolean full(List<String> lines, int maxLines) {
      return maxLines > 0 && lines.size() >= maxLines;
   }

   /** Index at which to break {@code text} so the part before it fits; always at least one character. */
   private static int lastFittingBreak(String text, int maxWidth, ToIntFunction<String> width) {
      int lo = 1;
      int hi = text.length();
      while (lo < hi) {
         int mid = (lo + hi + 1) >>> 1;
         if (width.applyAsInt(text.substring(0, mid)) <= maxWidth) {
            lo = mid;
         } else {
            hi = mid - 1;
         }
      }

      // Prefer the last space inside the fitting prefix (the character at lo may itself be the space to break on).
      int space = text.lastIndexOf(' ', Math.min(lo, text.length() - 1));
      if (space > 0) {
         return space;
      }

      if (lo < text.length() && Character.isHighSurrogate(text.charAt(lo - 1)) && lo > 1) {
         return lo - 1;
      }

      return lo;
   }
}
