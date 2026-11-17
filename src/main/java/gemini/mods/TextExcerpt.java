package gemini.mods;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a project's Markdown/HTML description into a few lines of plain text for the detail pane: images, HTML tags,
 * badges, code fences and table rows are dropped, links keep only their text, and heading/emphasis marks go.
 */
public final class TextExcerpt {
   private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
   private static final Pattern LINKED_IMAGE = Pattern.compile("\\[\\s*!\\[[^\\]]*\\]\\([^)]*\\)\\s*\\]\\([^)]*\\)");
   private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
   private static final Pattern TAG = Pattern.compile("<[^>]{0,400}>");
   private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|`|~~)");
   private static final Pattern BARE_URL = Pattern.compile("https?://\\S+");
   /** Emoji (outside the font) and the joiners and variation selectors that go with them. */
   private static final Pattern PICTOGRAPH = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{FE00}-\\x{FE0F}\\x{200D}\\x{20E3}]");

   private TextExcerpt() {
   }

   /** Paragraphs of plain text, at most {@code maxChars} characters in total (cut at a word). */
   public static List<String> paragraphs(String markdown, int maxChars) {
      List<String> out = new ArrayList<>();
      if (markdown == null || markdown.isBlank()) {
         return out;
      }

      String text = LINKED_IMAGE.matcher(markdown.replace("\r", "")).replaceAll("");
      text = IMAGE.matcher(text).replaceAll("");
      text = TAG.matcher(text).replaceAll("");
      text = LINK.matcher(text).replaceAll("$1");
      text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
      StringBuilder paragraph = new StringBuilder();
      int used = 0;
      boolean fence = false;
      for (String raw : text.split("\n")) {
         String line = raw.strip();
         if (line.startsWith("```")) {
            fence = !fence;
            continue;
         }

         if (fence || line.startsWith("|") || line.matches("[-=*_ ]{3,}")) {
            continue;
         }

         line = line.replaceFirst("^#{1,6}\\s*", "").replaceFirst("^>\\s*", "").replaceFirst("^([-*+]|\\d+\\.)\\s+", "");
         line = EMPHASIS.matcher(line).replaceAll("");
         line = BARE_URL.matcher(line).replaceAll("");
         line = PICTOGRAPH.matcher(line).replaceAll("").strip();
         if (line.isEmpty()) {
            used = flush(paragraph, out, used, maxChars);
            if (used >= maxChars) {
               break;
            }

            continue;
         }

         if (paragraph.length() > 0) {
            paragraph.append(' ');
         }

         paragraph.append(line);
      }

      flush(paragraph, out, used, maxChars);
      return out;
   }

   private static int flush(StringBuilder paragraph, List<String> out, int used, int maxChars) {
      String text = paragraph.toString().strip();
      paragraph.setLength(0);
      if (text.isEmpty() || used >= maxChars) {
         return used;
      }

      int room = maxChars - used;
      if (text.length() > room) {
         int cut = text.lastIndexOf(' ', room);
         text = (cut > room / 2 ? text.substring(0, cut) : text.substring(0, room)).strip() + "...";
      }

      out.add(text);
      return used + text.length();
   }
}
