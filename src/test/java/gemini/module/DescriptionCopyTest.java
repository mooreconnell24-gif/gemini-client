package gemini.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import gemini.command.CommandHelp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Module and setting descriptions are the tooltips in both menus, so they are read as plain copy: no hype words, no
 * exclamation marks and no comparisons with other clients. This scans the source for the description argument of
 * every {@code super(name, description, ...)} and {@code new XSetting(name, description, ...)} call. Setting names and
 * mode values are config keys and may legitimately contain a listed word ("Smart", "Neon"), so every name and mode
 * value declared in the same file is removed from a description before it is checked.
 */
class DescriptionCopyTest {
   /** Words that make a tooltip read like an advert, plus other clients' names. Matched as whole words, any case. */
   private static final List<String> BANNED = List.of(
      "crazy", "insane", "insanely", "ultimate", "next-gen", "nextgen", "neon", "sleek", "seamless", "seamlessly", "powerful", "advanced",
      "smart", "epic", "awesome", "blazing", "buttery", "cutting-edge", "state-of-the-art", "god-tier", "telemetry", "glassmorphic",
      "liquidbounce", "meteor", "wurst", "rusherhack", "future client", "impact client", "aristois", "sigma", "vape"
   );
   private static final String LITERAL = "\"(?:[^\"\\\\]|\\\\.)*\"";
   private static final String OPERAND = "(?:" + LITERAL + "|[\\w.]+)";
   /** super( or new XSetting( , a name expression, then the description: one or more literals or names joined by +. */
   private static final Pattern CALL = Pattern.compile(
      "(super|new\\s+(\\w*)Setting)\\s*\\(\\s*(" + OPERAND + "(?:\\s*\\+\\s*" + OPERAND + ")*)\\s*,\\s*(" + OPERAND + "(?:\\s*\\+\\s*" + OPERAND + ")*)\\s*([,)])"
   );
   private static final Pattern STRING = Pattern.compile(LITERAL);
   private static final Path[] ROOTS = {Path.of("src/main/java/gemini/module"), Path.of("src/main/java/gemini/render/ShapeSettings.java")};

   private record Description(String where, String text, Set<String> exempt) {
   }

   @Test
   void descriptionsHaveNoHypeOrExclamationMarks() throws IOException {
      List<Description> all = this.descriptions();
      assertTrue(all.size() > 1000, "the scan found the descriptions (" + all.size() + ")");
      List<String> problems = new ArrayList<>();
      for (Description d : all) {
         String problem = problem(d.text(), d.exempt());
         if (problem != null) {
            problems.add(d.where() + ": " + problem + " in \"" + d.text() + "\"");
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void commandHelpHasNoHypeOrExclamationMarks() {
      List<String> problems = new ArrayList<>();
      for (CommandHelp.Entry entry : CommandHelp.ENTRIES) {
         String problem = problem(entry.description(), Set.of());
         if (problem != null) {
            problems.add(entry.usage() + ": " + problem);
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void checkCatchesWhatItShould() {
      assertTrue(problem("Your arrows do crazy damage.", Set.of()) != null);
      assertTrue(problem("Places blocks!", Set.of()) != null);
      assertTrue(problem("LiquidBounce-style card.", Set.of()) != null);
      assertTrue(problem("Smart blocks when an attacker swings", Set.of("Smart")) == null);
      assertTrue(problem("Shows a smartphone icon.", Set.of()) == null);
      assertTrue(problem("Raises the damage of arrows you shoot.", Set.of()) == null);
   }

   /** Null when the text is fine, otherwise what is wrong with it. */
   static String problem(String text, Set<String> exempt) {
      if (text.indexOf('!') >= 0) {
         return "exclamation mark";
      }

      String checked = text;
      for (String word : exempt) {
         if (!word.isBlank()) {
            checked = checked.replaceAll("(?<![\\w-])" + Pattern.quote(word) + "(?![\\w-])", " ");
         }
      }

      String lower = checked.toLowerCase(Locale.ROOT);
      for (String word : BANNED) {
         if (Pattern.compile("(?<![\\w-])" + Pattern.quote(word) + "(?![\\w-])").matcher(lower).find()) {
            return "\"" + word + "\"";
         }
      }

      return null;
   }

   private List<Description> descriptions() throws IOException {
      List<Description> out = new ArrayList<>();
      for (Path root : ROOTS) {
         assertTrue(Files.exists(root), root + " exists (tests run from the project directory)");
         try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
               scan(file, Files.readString(file), out);
            }
         }
      }

      return out;
   }

   private static void scan(Path file, String source, List<Description> out) {
      // Names and mode values first, so a description that mentions one ("Smart blocks when...") is not flagged.
      Set<String> exempt = new HashSet<>();
      List<String[]> found = new ArrayList<>();
      Matcher m = CALL.matcher(source);
      while (m.find()) {
         String name = literals(m.group(3));
         exempt.add(name);
         if ("Mode".equals(m.group(2)) && ",".equals(m.group(5))) {
            exempt.addAll(modeValues(source, m.end()));
         }

         found.add(new String[]{name, literals(m.group(4)), Integer.toString(lineOf(source, m.start()))});
      }

      for (String[] f : found) {
         out.add(new Description(file.getFileName() + ":" + f[2] + " (" + f[0] + ")", f[1], exempt));
      }
   }

   /** The string literals after a ModeSetting's description, up to its closing parenthesis: the mode values. */
   private static List<String> modeValues(String source, int from) {
      List<String> values = new ArrayList<>();
      int depth = 1;
      int end = from;
      while (end < source.length() && depth > 0) {
         char c = source.charAt(end);
         if (c == '"') {
            Matcher lit = STRING.matcher(source);
            if (lit.find(end) && lit.start() == end) {
               values.add(unquote(lit.group()));
               end = lit.end();
               continue;
            }
         } else if (c == '(') {
            depth++;
         } else if (c == ')') {
            depth--;
         }

         end++;
      }

      return values;
   }

   private static String literals(String expression) {
      StringBuilder text = new StringBuilder();
      Matcher lit = STRING.matcher(expression);
      while (lit.find()) {
         text.append(unquote(lit.group()));
      }

      return text.toString();
   }

   private static String unquote(String literal) {
      return literal.substring(1, literal.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
   }

   private static int lineOf(String source, int index) {
      int line = 1;
      for (int i = 0; i < index; i++) {
         if (source.charAt(i) == '\n') {
            line++;
         }
      }

      return line;
   }
}
