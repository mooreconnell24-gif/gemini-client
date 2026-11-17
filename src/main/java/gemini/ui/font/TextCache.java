package gemini.ui.font;

import java.util.HashMap;
import java.util.function.Function;

/**
 * A bounded per-string cache. Looking up a string that is already cached allocates nothing ({@link String} keeps
 * its own hash), which is what per-frame text code needs. When the cache is full it is emptied in one go: UI text is
 * mostly a fixed set of labels plus a trickle of changing numbers, so a rare refill is cheaper than tracking use.
 */
public final class TextCache<V> {
   private final HashMap<String, V> entries;
   private final int capacity;
   private int resets;

   public TextCache(int capacity) {
      if (capacity < 1) {
         throw new IllegalArgumentException("capacity");
      }

      this.capacity = capacity;
      this.entries = new HashMap<>(Math.min(capacity, 256) * 2);
   }

   /** The cached value for {@code key}, made with {@code maker} on first use. Null keys and null values are not cached. */
   public V get(String key, Function<String, V> maker) {
      if (key == null) {
         return null;
      }

      V value = this.entries.get(key);
      if (value == null) {
         value = maker.apply(key);
         if (value != null) {
            if (this.entries.size() >= this.capacity) {
               this.entries.clear();
               this.resets++;
            }

            this.entries.put(key, value);
         }
      }

      return value;
   }

   /** The cached value or null, without creating one. */
   public V peek(String key) {
      return key == null ? null : this.entries.get(key);
   }

   public void clear() {
      this.entries.clear();
   }

   public int size() {
      return this.entries.size();
   }

   /** How many times the cache filled up and was emptied. */
   public int resets() {
      return this.resets;
   }
}
