package gemini.ui;

import java.util.Locale;

/**
 * Forgiving text matching for the menus: every query character has to appear in order, but not next to each other,
 * so "kaura" finds Kill Aura and "nofal" finds No Fall. Scores reward prefixes, word starts and runs of consecutive
 * characters; a query with several words must match all of them.
 */
public final class Fuzzy {
   private Fuzzy() {
   }

   /** 0 when the text does not match, higher is a better match. */
   public static int score(String query, String text) {
      if (query == null || text == null) {
         return 0;
      }

      String q = query.trim().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) {
         return 1;
      }

      String t = text.toLowerCase(Locale.ROOT);
      if (q.indexOf(' ') >= 0) {
         int total = 0;
         for (String word : q.split("\\s+")) {
            int s = scoreWord(word, t, text);
            if (s == 0) {
               return 0;
            }

            total += s;
         }

         return total;
      }

      return scoreWord(q, t, text);
   }

   private static int scoreWord(String q, String t, String original) {
      int direct = t.indexOf(q);
      if (direct >= 0) {
         // substring matches are always strong; earlier and at a word start is stronger
         int score = 100 + q.length() * 4 - Math.min(40, direct);
         if (direct == 0) {
            score += 40;
         } else if (wordStart(original, direct)) {
            score += 20;
         }

         return score;
      }

      int score = 0;
      int ti = 0;
      int lastHit = -2;
      for (int qi = 0; qi < q.length(); qi++) {
         char c = q.charAt(qi);
         int hit = t.indexOf(c, ti);
         if (hit < 0) {
            return 0;
         }

         score += 6;
         if (hit == lastHit + 1) {
            score += 5;
         } else if (hit == 0) {
            score += 10;
         } else if (wordStart(original, hit)) {
            score += 8;
         }

         score -= Math.min(6, hit - ti);
         lastHit = hit;
         ti = hit + 1;
      }

      return Math.max(1, score);
   }

   private static boolean wordStart(String text, int index) {
      if (index == 0) {
         return true;
      }

      char prev = text.charAt(index - 1);
      char cur = text.charAt(index);
      return prev == ' ' || prev == '_' || prev == '-' || prev == '.' || Character.isLowerCase(prev) && Character.isUpperCase(cur);
   }

   /** A module's score and, when it only matched through one of its settings, that setting's name. */
   public record Match(int score, String setting) {
      public static final Match NONE = new Match(0, null);

      public boolean matched() {
         return this.score > 0;
      }
   }

   /**
    * How well a module matches: its name and aliases count fully, its description and the names of its settings
    * a little less, so "webs ahead" lists Web Aura (through its Webs Ahead setting) below direct hits on a module name.
    */
   public static int moduleScore(String query, gemini.module.Module module) {
      return moduleMatch(query, module).score();
   }

   /** As {@link #moduleScore}, also telling which setting produced the match (for "matched: Range" hints). */
   public static Match moduleMatch(String query, gemini.module.Module module) {
      java.util.List<String> settings = new java.util.ArrayList<>();
      for (gemini.module.setting.Setting<?> setting : module.settings()) {
         settings.add(setting.name());
      }

      return match(query, module.getName(), module.aliases(), module.getDescription(), settings);
   }

   /**
    * The matching rule behind {@link #moduleMatch}, on plain strings. {@link Match#setting()} is set only when a
    * setting name scored strictly higher than the name, the aliases and the description did.
    */
   public static Match match(String query, String name, Iterable<String> aliases, String description, Iterable<String> settingNames) {
      if (query == null || query.isBlank()) {
         return new Match(1, null);
      }

      int best = score(query, name);
      if (aliases != null) {
         for (String alias : aliases) {
            best = Math.max(best, score(query, alias));
         }
      }

      // Descriptions and setting names are long, so a scattered-letter match there is almost always noise
      // ("heal" used to pull in 68 unrelated modules). They only count when the words actually appear.
      best = Math.max(best, substringScore(query, description) * 3 / 5);
      String matchedSetting = null;
      if (settingNames != null) {
         for (String setting : settingNames) {
            int s = substringScore(query, setting) * 7 / 10;
            if (s > best) {
               best = s;
               matchedSetting = setting;
            }
         }
      }

      return best <= 0 ? Match.NONE : new Match(best, matchedSetting);
   }

   /** Like {@link #score} but every query word must appear in the text as written (no scattered letters). */
   public static int substringScore(String query, String text) {
      if (query == null || text == null) {
         return 0;
      }

      String q = query.trim().toLowerCase(Locale.ROOT);
      if (q.isEmpty()) {
         return 1;
      }

      String t = text.toLowerCase(Locale.ROOT);
      for (String word : q.split("\\s+")) {
         if (!t.contains(word)) {
            return 0;
         }
      }

      return score(q, text);
   }

   /** The best score of the query against any of the texts. */
   public static int best(String query, Iterable<String> texts) {
      int best = 0;
      for (String text : texts) {
         best = Math.max(best, score(query, text));
      }

      return best;
   }
}
