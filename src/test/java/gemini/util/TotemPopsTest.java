package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotemPopsTest {
   private final UUID alice = UUID.randomUUID();
   private final UUID zombie = UUID.randomUUID();

   @Test
   void countsPopsPerEntity() {
      TotemPops.Ledger ledger = new TotemPops.Ledger();
      assertEquals(1, ledger.pop(this.alice, 10));
      assertEquals(2, ledger.pop(this.alice, 10));
      assertEquals(1, ledger.pop(this.zombie, 11));
      assertEquals(2, ledger.count(this.alice));
      assertEquals(1, ledger.count(this.zombie));
      assertEquals(0, ledger.count(UUID.randomUUID()));
      assertEquals(2, ledger.size());
   }

   @Test
   void deathIsReportedOnceAndForgetsWhenAsked() {
      TotemPops.Ledger ledger = new TotemPops.Ledger();
      ledger.pop(this.alice, 10);
      ledger.pop(this.alice, 10);
      assertEquals(2, ledger.died(this.alice, false), "the first report of a death carries the pop count");
      assertEquals(-1, ledger.died(this.alice, false), "the same death is not reported twice");
      assertEquals(2, ledger.count(this.alice), "kept when not forgetting (reset only on world change)");
      ledger.alive(this.alice);
      assertEquals(2, ledger.died(this.alice, true), "a new death after being seen alive is reported again");
      assertEquals(0, ledger.count(this.alice), "and forgotten");
      assertEquals(-1, ledger.died(this.zombie, true), "an entity that never popped has no death to report");
   }

   @Test
   void aPopAfterDeathStartsAlive() {
      TotemPops.Ledger ledger = new TotemPops.Ledger();
      ledger.pop(this.alice, 10);
      ledger.died(this.alice, false);
      assertEquals(2, ledger.pop(this.alice, 12));
      assertEquals(2, ledger.died(this.alice, false));
   }

   @Test
   void resetForgetsEveryone() {
      TotemPops.Ledger ledger = new TotemPops.Ledger();
      ledger.pop(this.alice, 1);
      ledger.pop(this.zombie, 2);
      ledger.forget(this.zombie);
      assertEquals(1, ledger.size());
      ledger.reset();
      assertTrue(ledger.isEmpty());
   }
}
