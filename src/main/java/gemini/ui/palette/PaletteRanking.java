package gemini.ui.palette;

import gemini.ui.Fuzzy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ranks quick-search candidates for a query. Pure (candidates are plain strings plus an opaque payload), so the
 * ordering rules are unit tested:
 *
 * <ul>
 *   <li>matching uses {@link Fuzzy#match}: names and aliases fully, descriptions and setting names only when the
 *       words really appear (and then a setting match is reported, for the "setting: Range" hint);</li>
 *   <li>things picked recently get a small boost, and with an empty query they are listed first, newest first;</li>
 *   <li>ties go to the kind order (modules, pages, commands) and then the name, case-insensitively.</li>
 * </ul>
 */
public final class PaletteRanking {
   /** Score added for the most recent pick; older picks get proportionally less. */
   static final int RECENT_BOOST = 30;

   /** What a result is. {@link #ALL} is only a filter. */
   public enum Kind {
      ALL("All"),
      MODULE("Modules"),
      PAGE("Pages"),
      COMMAND("Commands");

      public final String label;

      Kind(String label) {
         this.label = label;
      }

      /** The next filter for Tab (or the previous for Shift+Tab). */
      public Kind cycle(int step) {
         Kind[] all = values();
         return all[Math.floorMod(this.ordinal() + step, all.length)];
      }
   }

   /**
    * Something the palette can find. {@code key} identifies it across openings (for recents), {@code settings} are
    * setting names (modules only) and {@code payload} is whatever the screen needs to act on it.
    */
   public record Candidate(Kind kind, String key, String name, List<String> aliases, String description, List<String> settings, Object payload) {
   }

   /** A ranked candidate; {@code setting} is set when only a setting name matched. */
   public record Result(Candidate candidate, int score, String setting) {
   }

   private PaletteRanking() {
   }

   /**
    * The candidates matching {@code query} of kind {@code filter} (ALL for any), best first, at most {@code limit}.
    * {@code recent} holds candidate keys, most recent first.
    */
   public static List<Result> rank(String query, List<Candidate> candidates, Kind filter, List<String> recent, int limit) {
      String q = query == null ? "" : query.trim();
      List<Result> results = new ArrayList<>();
      for (Candidate candidate : candidates) {
         if (filter != Kind.ALL && filter != null && candidate.kind() != filter) {
            continue;
         }

         int recency = recent == null ? -1 : indexOf(recent, candidate.key());
         int boost = recency < 0 ? 0 : RECENT_BOOST * (recent.size() - recency) / recent.size();
         if (q.isEmpty()) {
            results.add(new Result(candidate, 1 + boost, null));
            continue;
         }

         Fuzzy.Match match = Fuzzy.match(q, candidate.name(), candidate.aliases(), candidate.description(), candidate.settings());
         if (match.matched()) {
            results.add(new Result(candidate, match.score() + boost, match.setting()));
         }
      }

      results.sort(
         Comparator.comparingInt((Result r) -> -r.score())
            .thenComparingInt(r -> r.candidate().kind().ordinal())
            .thenComparing(r -> r.candidate().name().toLowerCase(Locale.ROOT))
      );
      return results.size() > limit ? List.copyOf(results.subList(0, Math.max(0, limit))) : results;
   }

   /** {@code recent} with {@code key} moved to the front, trimmed to {@code max} entries. */
   public static List<String> remember(List<String> recent, String key, int max) {
      List<String> next = new ArrayList<>(max);
      next.add(key);
      for (String old : recent) {
         if (next.size() >= max) {
            break;
         }

         if (!old.equals(key)) {
            next.add(old);
         }
      }

      return next;
   }

   private static int indexOf(List<String> list, String key) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).equals(key)) {
            return i;
         }
      }

      return -1;
   }
}
