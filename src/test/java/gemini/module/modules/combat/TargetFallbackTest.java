package gemini.module.modules.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TargetFallbackTest {
   /** A picker over a list already in priority order: the first entry the filter lets through. */
   private static Function<Predicate<String>, String> inOrder(List<String> ranked) {
      return filter -> {
         for (String s : ranked) {
            if (filter.test(s)) {
               return s;
            }
         }

         return null;
      };
   }

   @Test
   void priorityTargetWinsWhenViable() {
      assertEquals("a", TargetFallback.first(inOrder(List.of("a", "b", "c")), s -> true, 3));
   }

   @Test
   void fallsBackInPriorityOrder() {
      List<String> asked = new ArrayList<>();
      String picked = TargetFallback.first(inOrder(List.of("a", "b", "c")), s -> {
         asked.add(s);
         return s.equals("c");
      }, 3);
      assertEquals("c", picked);
      assertEquals(List.of("a", "b", "c"), asked);
   }

   @Test
   void givesUpAfterTries() {
      List<String> asked = new ArrayList<>();
      assertNull(TargetFallback.first(inOrder(List.of("a", "b", "c")), s -> {
         asked.add(s);
         return s.equals("c");
      }, 2));
      assertEquals(List.of("a", "b"), asked);
   }

   @Test
   void stopsWhenNoCandidateLeft() {
      assertNull(TargetFallback.first(inOrder(List.of("a")), s -> false, 3));
      assertNull(TargetFallback.first(inOrder(List.of()), s -> true, 3));
   }

   @Test
   void exclusionIsByIdentity() {
      String first = new String("x");
      String second = new String("x");
      assertSame(second, TargetFallback.first(inOrder(List.of(first, second)), s -> s == second, 3));
   }

   @Test
   void zeroTriesPicksNothing() {
      assertNull(TargetFallback.first(inOrder(List.of("a")), s -> true, 0));
   }
}
