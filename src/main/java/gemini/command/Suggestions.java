package gemini.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Suggestions {
   private Suggestions() {
   }

   public static String normalize(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
   }

   public static List<String> closest(String input, Collection<String> candidates, int max) {
      String needle = normalize(input);
      List<Suggestions.Scored> scored = new ArrayList<>();
      if (!needle.isEmpty() && max > 0) {
         Set<String> seen = new LinkedHashSet<>();

         for (String candidate : candidates) {
            if (candidate != null && seen.add(candidate)) {
               String hay = normalize(candidate);
               if (!hay.isEmpty() && !hay.equals(needle)) {
                  int score;
                  if (hay.startsWith(needle) && needle.length() >= 2) {
                     score = 0;
                  } else if (hay.contains(needle) && needle.length() >= 3) {
                     score = 1;
                  } else {
                     int distance = distance(needle, hay);
                     int allowed = Math.max(1, needle.length() / 3);
                     if (distance > allowed) {
                        continue;
                     }

                     score = 1 + distance;
                  }

                  scored.add(new Suggestions.Scored(candidate, hay.length(), score));
               }
            }
         }

         scored.sort(
            Comparator.comparingInt(Suggestions.Scored::score)
               .thenComparingInt(Suggestions.Scored::length)
               .thenComparing(Suggestions.Scored::name, String.CASE_INSENSITIVE_ORDER)
         );
         List<String> result = new ArrayList<>();

         for (Suggestions.Scored entry : scored) {
            if (result.size() >= max) {
               break;
            }

            result.add(entry.name());
         }

         return result;
      } else {
         return List.of();
      }
   }

   public static int distance(String a, String b) {
      int[][] d = new int[a.length() + 1][b.length() + 1];
      int i = 0;

      while (i <= a.length()) {
         d[i][0] = i++;
      }

      i = 0;

      while (i <= b.length()) {
         d[0][i] = i++;
      }

      for (int ix = 1; ix <= a.length(); ix++) {
         for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(ix - 1) == b.charAt(j - 1) ? 0 : 1;
            d[ix][j] = Math.min(d[ix - 1][j - 1] + cost, Math.min(d[ix - 1][j] + 1, d[ix][j - 1] + 1));
            if (ix > 1 && j > 1 && a.charAt(ix - 1) == b.charAt(j - 2) && a.charAt(ix - 2) == b.charAt(j - 1)) {
               d[ix][j] = Math.min(d[ix][j], d[ix - 2][j - 2] + 1);
            }
         }
      }

      return d[a.length()][b.length()];
   }

   public static String humanList(List<String> items) {
      if (items.isEmpty()) {
         return "";
      } else {
         return items.size() == 1 ? items.getFirst() : String.join(", ", items.subList(0, items.size() - 1)) + " or " + items.getLast();
      }
   }

   private record Scored(String name, int length, int score) {
   }
}
