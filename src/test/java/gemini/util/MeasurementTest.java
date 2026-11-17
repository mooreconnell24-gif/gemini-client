package gemini.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MeasurementTest {
   @Test
   void neighboursAreOneMetreApartInATwoBlockBox() {
      Measurement m = new Measurement(0, 64, 0, 1, 64, 0);
      assertEquals(1.0, m.distance(), 1.0E-9);
      assertEquals(2, m.sizeX());
      assertEquals(1, m.sizeY());
      assertEquals(2L, m.volume());
      assertEquals(1, m.gridSteps());
   }

   @Test
   void boxCountsBothEndsWhicheverWayRound() {
      Measurement m = new Measurement(10, 70, -5, 6, 68, 3);
      assertEquals(5, m.sizeX());
      assertEquals(3, m.sizeY());
      assertEquals(9, m.sizeZ());
      assertEquals(135L, m.volume());
      assertEquals(45L, m.floorArea());
      assertEquals(4 + 2 + 8, m.gridSteps());
      assertEquals(Math.sqrt(16 + 4 + 64), m.distance(), 1.0E-9);
      assertEquals(Math.sqrt(16 + 64), m.horizontal(), 1.0E-9);
      assertEquals("5 x 3 x 9 = 135 blocks", m.sizeLabel());
      assertEquals("9.2 m", m.distanceLabel());
   }

   @Test
   void oneBlockAndHugeBoxes() {
      assertEquals("1 x 1 x 1 = 1 block", new Measurement(3, 3, 3, 3, 3, 3).sizeLabel());
      assertEquals("0.0 m", new Measurement(3, 3, 3, 3, 3, 3).distanceLabel());
      Measurement big = new Measurement(-30_000_000, -64, -30_000_000, 30_000_000, 319, 30_000_000);
      assertTrue(big.volume() > Integer.MAX_VALUE, "no int overflow");
   }

   @Test
   void labelsAreBuiltOnceAndUseADecimalPoint() {
      Locale before = Locale.getDefault();
      try {
         Locale.setDefault(Locale.GERMANY);
         Measurement m = new Measurement(0, 0, 0, 1, 1, 0);
         assertEquals("1.4 m", m.distanceLabel());
         assertSame(m.distanceLabel(), m.distanceLabel());
         assertSame(m.sizeLabel(), m.sizeLabel());
         assertTrue(m.summary().startsWith("From 0 0 0 to 1 1 0: 1.4 m"));
      } finally {
         Locale.setDefault(before);
      }
   }

   @Test
   void sameComparesAllCorners() {
      Measurement m = new Measurement(1, 2, 3, 4, 5, 6);
      assertTrue(m.same(1, 2, 3, 4, 5, 6));
      assertTrue(!m.same(1, 2, 3, 4, 5, 7));
   }
}
