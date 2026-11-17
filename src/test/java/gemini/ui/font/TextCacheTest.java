package gemini.ui.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TextCacheTest {
   @Test
   void aStringIsMadeOnceAndThenServedFromTheCache() {
      AtomicInteger made = new AtomicInteger();
      TextCache<int[]> cache = new TextCache<>(8);
      int[] first = cache.get("Speed", text -> new int[] {made.incrementAndGet()});
      int[] second = cache.get("Speed", text -> new int[] {made.incrementAndGet()});
      assertSame(first, second);
      assertEquals(1, made.get());
      // an equal string that is a different object hits the same entry
      assertSame(first, cache.get(new String("Speed"), text -> new int[] {made.incrementAndGet()}));
      assertEquals(1, made.get());
      assertEquals(1, cache.size());
      assertSame(first, cache.peek("Speed"));
      assertNull(cache.peek("Flight"));
   }

   @Test
   void aFullCacheIsEmptiedAndRefills() {
      TextCache<String> cache = new TextCache<>(4);
      for (int i = 0; i < 4; i++) {
         cache.get("fps " + i, text -> text.toUpperCase());
      }

      assertEquals(4, cache.size());
      assertEquals(0, cache.resets());
      assertEquals("FPS 4", cache.get("fps 4", text -> text.toUpperCase()));
      assertEquals(1, cache.resets());
      assertEquals(1, cache.size(), "only the newest entry survives the reset");
      assertNull(cache.peek("fps 0"));
      assertEquals("FPS 4", cache.peek("fps 4"));
      // it never holds more than its capacity however many strings pass through
      for (int i = 0; i < 1000; i++) {
         cache.get("value " + i, text -> text);
         assertEquals(true, cache.size() <= 4);
      }
   }

   @Test
   void nullKeysAndNullValuesAreNotCached() {
      TextCache<String> cache = new TextCache<>(4);
      assertNull(cache.get(null, text -> "x"));
      assertNull(cache.get("key", text -> null));
      assertEquals(0, cache.size());
      assertEquals("made", cache.get("key", text -> "made"));
      cache.clear();
      assertEquals(0, cache.size());
      assertThrows(IllegalArgumentException.class, () -> new TextCache<String>(0));
   }
}
