package gemini.ui.clickgui.window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * The rows of the profile browser: which profiles exist, when each was last written and which one is active,
 * in the order the page shows them. Pure data so it can be tested without a game.
 */
public final class ProfileList {
   /** Stands for a file whose modification time could not be read. */
   public static final long UNKNOWN = -1L;

   private ProfileList() {
   }

   public enum Sort {
      RECENT("Recent"),
      NAME("Name");

      public final String label;

      Sort(String label) {
         this.label = label;
      }

      public Sort next() {
         return values()[(this.ordinal() + 1) % values().length];
      }
   }

   public record Entry(String name, long modified, boolean active) {
   }

   /**
    * One entry per name. The active profile always comes first; the rest follow {@code sort}: newest first for
    * {@link Sort#RECENT} (unknown times last, ties by name), case-insensitive alphabetical for {@link Sort#NAME}.
    */
   public static List<Entry> build(List<String> names, ToLongFunction<String> modified, String active, Sort sort) {
      List<Entry> entries = new ArrayList<>();
      for (String name : names) {
         if (name != null && !name.isEmpty()) {
            entries.add(new Entry(name, modified.applyAsLong(name), name.equals(active)));
         }
      }

      Comparator<Entry> byName = Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::name);
      Comparator<Entry> order = sort == Sort.RECENT
         ? Comparator.comparingLong((Entry e) -> e.modified() < 0L ? Long.MIN_VALUE : e.modified()).reversed().thenComparing(byName)
         : byName;
      entries.sort(Comparator.comparing((Entry e) -> !e.active()).thenComparing(order));
      return entries;
   }

   /** "just now", "5 min ago", "3 h ago", "2 d ago", "4 wk ago", or "unknown" for a missing time. */
   public static String ago(long nowMs, long thenMs) {
      if (thenMs < 0L) {
         return "unknown";
      }

      long seconds = Math.max(0L, (nowMs - thenMs) / 1000L);
      if (seconds < 45L) {
         return "just now";
      } else if (seconds < 3600L) {
         return Math.max(1L, Math.round(seconds / 60.0)) + " min ago";
      } else if (seconds < 86400L) {
         return seconds / 3600L + " h ago";
      } else if (seconds < 86400L * 14L) {
         return seconds / 86400L + " d ago";
      } else {
         return seconds / (86400L * 7L) + " wk ago";
      }
   }
}
