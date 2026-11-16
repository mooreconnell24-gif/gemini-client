package gemini.ui.help;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The small Markdown subset the in-game changelog is written in: {@code #} headings, paragraphs, {@code -} / {@code *}
 * bullets (indented by two spaces per level), pipe tables and {@code ---} rules. Inline {@code **bold**},
 * {@code *italic*}, {@code `code`} and {@code [text](url)} are turned into Minecraft formatting codes (bold text is
 * drawn bright, code in aqua) so the font renderer styles them and {@link TextWrap} can wrap them. HTML tags such as
 * {@code <details>} are dropped. Pure, so it is unit tested.
 */
public final class Markdown {
   /** Formatting code for bold text: bright white. */
   public static final String BOLD = "§f";
   /** Formatting code for inline code: aqua. */
   public static final String CODE = "§b";
   /** Back to the colour the line is drawn with. */
   public static final String RESET = "§r";
   private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
   private static final Pattern BOLD_SPAN = Pattern.compile("\\*\\*(.+?)\\*\\*");
   private static final Pattern ITALIC_SPAN = Pattern.compile("(?<![\\w*])\\*([^*\\s][^*]*?)\\*(?![\\w*])");
   private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
   private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
   private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$");

   private Markdown() {
   }

   /** One block of the document. */
   public sealed interface Block permits Heading, Paragraph, Bullet, Table, Rule {
   }

   public record Heading(int level, String text) implements Block {
   }

   public record Paragraph(String text) implements Block {
   }

   /** {@code depth} 0 is a top-level bullet. */
   public record Bullet(int depth, String text) implements Block {
   }

   public record Table(List<String> header, List<List<String>> rows) implements Block {
      public int columns() {
         return this.header.size();
      }
   }

   public record Rule() implements Block {
   }

   public static List<Block> parse(String source) {
      List<Block> blocks = new ArrayList<>();
      if (source == null) {
         return blocks;
      }

      String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
      StringBuilder paragraph = new StringBuilder();
      int i = 0;
      while (i < lines.length) {
         String raw = lines[i];
         String line = TAG.matcher(raw).replaceAll("").stripTrailing();
         String trimmed = line.strip();
         if (trimmed.isEmpty()) {
            flush(paragraph, blocks);
            i++;
            continue;
         }

         if (trimmed.startsWith("#")) {
            flush(paragraph, blocks);
            int level = 0;
            while (level < trimmed.length() && trimmed.charAt(level) == '#') {
               level++;
            }

            blocks.add(new Heading(Math.min(level, 4), inline(trimmed.substring(level).strip())));
            i++;
            continue;
         }

         if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
            flush(paragraph, blocks);
            blocks.add(new Rule());
            i++;
            continue;
         }

         if (isBullet(trimmed)) {
            flush(paragraph, blocks);
            int indent = leadingSpaces(line);
            StringBuilder text = new StringBuilder(trimmed.substring(2).strip());
            i++;
            // Lines indented under a bullet, that are not bullets themselves, continue it.
            while (i < lines.length) {
               String next = TAG.matcher(lines[i]).replaceAll("").stripTrailing();
               String nextTrimmed = next.strip();
               if (nextTrimmed.isEmpty() || isBullet(nextTrimmed) || nextTrimmed.startsWith("#") || nextTrimmed.startsWith("|") || leadingSpaces(next) <= indent) {
                  break;
               }

               text.append(' ').append(nextTrimmed);
               i++;
            }

            blocks.add(new Bullet(indent / 2, inline(text.toString())));
            continue;
         }

         if (trimmed.startsWith("|") && i + 1 < lines.length && TABLE_DIVIDER.matcher(lines[i + 1].strip()).matches()) {
            flush(paragraph, blocks);
            List<String> header = cells(trimmed);
            List<List<String>> rows = new ArrayList<>();
            i += 2;
            while (i < lines.length && lines[i].strip().startsWith("|")) {
               List<String> row = cells(lines[i].strip());
               // Pad or cut every row to the header's width so a sloppy row cannot break the layout.
               while (row.size() < header.size()) {
                  row.add("");
               }

               rows.add(List.copyOf(row.subList(0, header.size())));
               i++;
            }

            blocks.add(new Table(List.copyOf(header), List.copyOf(rows)));
            continue;
         }

         if (!paragraph.isEmpty()) {
            paragraph.append(' ');
         }

         paragraph.append(trimmed);
         i++;
      }

      flush(paragraph, blocks);
      return blocks;
   }

   /** The text of the first heading whose text starts with a version number ("3.3.0"), or null. */
   public static String firstVersion(List<Block> blocks) {
      for (Block block : blocks) {
         if (block instanceof Heading heading) {
            String plain = strip(heading.text());
            Matcher m = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(plain);
            if (m.find()) {
               return m.group(1);
            }
         }
      }

      return null;
   }

   /** Inline Markdown to formatting codes. */
   public static String inline(String text) {
      String out = text.replace("\\|", "|");
      out = LINK.matcher(out).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + " (" + match.group(2) + ")"));
      out = CODE_SPAN.matcher(out).replaceAll(match -> Matcher.quoteReplacement(CODE + match.group(1) + RESET));
      out = BOLD_SPAN.matcher(out).replaceAll(match -> Matcher.quoteReplacement(BOLD + match.group(1) + RESET));
      out = ITALIC_SPAN.matcher(out).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
      return out;
   }

   /** The text without formatting codes. */
   public static String strip(String formatted) {
      return formatted == null ? "" : formatted.replaceAll("§.", "");
   }

   private static boolean isBullet(String trimmed) {
      return trimmed.length() > 2 && (trimmed.charAt(0) == '-' || trimmed.charAt(0) == '*' || trimmed.charAt(0) == '+') && trimmed.charAt(1) == ' ';
   }

   private static int leadingSpaces(String line) {
      int n = 0;
      while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
         n += line.charAt(n) == '\t' ? 4 : 1;
         if (n > 64) {
            break;
         }
      }

      return Math.min(n, line.length() * 4);
   }

   private static List<String> cells(String row) {
      String body = row;
      if (body.startsWith("|")) {
         body = body.substring(1);
      }

      if (body.endsWith("|") && !body.endsWith("\\|")) {
         body = body.substring(0, body.length() - 1);
      }

      List<String> cells = new ArrayList<>();
      // Split on pipes that are not escaped as \|.
      StringBuilder cell = new StringBuilder();
      for (int i = 0; i < body.length(); i++) {
         char c = body.charAt(i);
         if (c == '\\' && i + 1 < body.length() && body.charAt(i + 1) == '|') {
            cell.append('|');
            i++;
         } else if (c == '|') {
            cells.add(inline(cell.toString().strip()));
            cell.setLength(0);
         } else {
            cell.append(c);
         }
      }

      cells.add(inline(cell.toString().strip()));
      return cells;
   }

   private static void flush(StringBuilder paragraph, List<Block> blocks) {
      if (!paragraph.isEmpty()) {
         blocks.add(new Paragraph(inline(paragraph.toString())));
         paragraph.setLength(0);
      }
   }
}
