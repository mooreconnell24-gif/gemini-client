package gemini.module.modules.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArrayListLayoutTest {
   /** Stand-ins for modules: compared by identity, like the real keys. */
   private static final class Key {
      final String name;

      Key(String name) {
         this.name = name;
      }
   }

   private final Key flight = new Key("Flight");
   private final Key esp = new Key("ESP");
   private final Key killAura = new Key("KillAura");
   private int measured;
   private ArrayListLayout<Key> layout;
   /** Moves half the remaining way each step: a deterministic stand-in for the exponential approach. */
   private final ArrayListLayout.Glide half = (current, target, delta) -> {
      float next = current + (target - current) * 0.5F;
      return Math.abs(target - next) < 0.01F ? target : next;
   };

   @BeforeEach
   void setUp() {
      this.measured = 0;
      this.layout = new ArrayListLayout<>(text -> {
         this.measured++;
         return text.length() * 6;
      }, new ArrayListLayout.Decorator<>() {
         @Override
         public String label(Key key, String raw) {
            return raw;
         }

         @Override
         public String info(Key key, String raw) {
            return "[" + raw + "]";
         }
      });
   }

   private void poll(Object... entries) {
      this.layout.beginPoll();
      for (int i = 0; i < entries.length; i += 2) {
         Key key = (Key)entries[i];
         this.layout.offer(key, key.name, -1, (String)entries[i + 1], 0);
      }

      this.layout.endPoll();
   }

   private List<String> order() {
      List<String> names = new ArrayList<>();
      for (ArrayListLayout.Row<Key> row : this.layout.rows()) {
         names.add(row.key.name);
      }

      return names;
   }

   @Test
   void sortsByWidthIncludingTheInfoSuffix() {
      this.poll(this.flight, null, this.esp, "Players and Mobs", this.killAura, "Single");
      assertEquals(List.of("ESP", "KillAura", "Flight"), this.order());
      ArrayListLayout.Row<Key> espRow = this.layout.rows().get(0);
      assertEquals("[Players and Mobs]", espRow.info);
      assertEquals(18, espRow.labelWidth);
      assertEquals(18 + ArrayListLayout.INFO_GAP + 18 * 6, espRow.width);
      assertEquals(espRow.width, this.layout.maxWidth());
      assertNull(this.layout.rows().get(2).info, "no info means no suffix");
   }

   @Test
   void sortsAlphabeticallyCaseInsensitive() {
      this.layout.setSortMode(ArrayListLayout.SORT_ALPHABETICAL);
      this.poll(this.killAura, null, this.esp, null, this.flight, null);
      assertEquals(List.of("ESP", "Flight", "KillAura"), this.order());
   }

   @Test
   void anIdlePollDoesNoWork() {
      this.poll(this.flight, "Vanilla", this.esp, null);
      int sorts = this.layout.sortCount();
      int measures = this.measured;
      this.poll(this.flight, "Vanilla", this.esp, null);
      this.poll(this.esp, null, this.flight, "Vanilla");
      assertEquals(sorts, this.layout.sortCount(), "nothing changed, so nothing is re-sorted");
      assertEquals(measures, this.measured, "nothing changed, so nothing is re-measured");
   }

   @Test
   void aChangedInfoReMeasuresOnlyThatRow() {
      this.poll(this.flight, "Vanilla", this.esp, null);
      int measures = this.measured;
      this.poll(this.flight, "Creative Flight Mode", this.esp, null);
      assertEquals(measures + 2, this.measured, "label and info of the one changed row");
      assertSame(this.flight, this.layout.rows().get(0).key);
   }

   @Test
   void newRowsSlideInAndRemovedRowsStayUntilTheyHaveSlidOut() {
      this.poll(this.flight, null);
      ArrayListLayout.Row<Key> row = this.layout.rows().get(0);
      assertEquals(0.0F, row.anim);
      assertTrue(row.active);
      for (int i = 0; i < 20; i++) {
         this.layout.advance(0.016F, this.half);
      }

      assertEquals(1.0F, row.anim);
      this.poll();
      assertFalse(row.active);
      assertEquals(1, this.layout.size(), "a disabled module keeps its row while it slides out");
      this.layout.advance(0.016F, this.half);
      assertTrue(row.anim > 0.0F && row.anim < 1.0F);
      assertEquals(1, this.layout.size());
      assertEquals(10.0F * row.anim, this.layout.height(10.0F), 1.0E-4F, "the row's height collapses with it");
      for (int i = 0; i < 20; i++) {
         this.layout.advance(0.016F, this.half);
      }

      assertEquals(0, this.layout.size(), "removed once it has fully slid out");
      assertEquals(0, this.layout.maxWidth());
   }

   @Test
   void aRowComingBackWhileSlidingOutReversesWithoutJumping() {
      this.poll(this.flight, null);
      this.layout.advance(0.016F, null);
      this.poll();
      this.layout.advance(0.016F, this.half);
      ArrayListLayout.Row<Key> row = this.layout.rows().get(0);
      float midway = row.anim;
      this.poll(this.flight, null);
      assertSame(row, this.layout.rows().get(0), "the same row object comes back");
      assertTrue(row.active);
      assertEquals(midway, row.anim, "it continues from where it was");
      this.layout.advance(0.016F, this.half);
      assertTrue(row.anim > midway);
   }

   @Test
   void withoutAnimationRowsSnap() {
      this.poll(this.flight, null, this.esp, null);
      this.layout.advance(0.016F, null);
      assertEquals(20.0F, this.layout.height(10.0F));
      this.poll(this.esp, null);
      this.layout.advance(0.016F, null);
      assertEquals(List.of("ESP"), this.order());
   }

   @Test
   void invalidateRedecoratesEverything() {
      this.poll(this.flight, null, this.esp, null);
      int measures = this.measured;
      this.layout.invalidate();
      this.poll(this.flight, null, this.esp, null);
      assertEquals(measures + 2, this.measured);
   }
}
