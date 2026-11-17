package gemini.module.modules.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Label text per entity id, built again only when what it shows changes.
 *
 * <p>World labels are asked for every frame. The caller describes what a label shows as a source object (compared
 * by identity, e.g. the item stack) and a signature (the numbers in it packed into a long); while both match, the
 * cached text comes back and nothing is built. Ids not asked for in {@value #STALE_FRAMES} frames are dropped, so
 * entities that left do not pile up. Client thread only.
 */
final class LabelCache {
   static final int STALE_FRAMES = 120;
   private final Int2ObjectOpenHashMap<Slot> slots = new Int2ObjectOpenHashMap<>();
   private int frame;

   private static final class Slot {
      Object source;
      long signature;
      String text;
      int lastUsed;
   }

   /** Call once per frame before the first lookup; every {@value #STALE_FRAMES} frames, unused ids are dropped. */
   void beginFrame() {
      this.frame++;
      if (this.frame % STALE_FRAMES == 0 && !this.slots.isEmpty()) {
         ObjectIterator<Int2ObjectMap.Entry<Slot>> it = this.slots.int2ObjectEntrySet().fastIterator();
         while (it.hasNext()) {
            if (this.frame - it.next().getValue().lastUsed >= STALE_FRAMES) {
               it.remove();
            }
         }
      }
   }

   /** The cached text for {@code id}, or null when there is none or its source or signature changed. */
   String get(int id, Object source, long signature) {
      Slot slot = this.slots.get(id);
      if (slot == null || slot.source != source || slot.signature != signature) {
         return null;
      }

      slot.lastUsed = this.frame;
      return slot.text;
   }

   /** Stores and returns {@code text} for {@code id}. */
   String put(int id, Object source, long signature, String text) {
      Slot slot = this.slots.get(id);
      if (slot == null) {
         slot = new Slot();
         this.slots.put(id, slot);
      }

      slot.source = source;
      slot.signature = signature;
      slot.text = text;
      slot.lastUsed = this.frame;
      return text;
   }

   int size() {
      return this.slots.size();
   }

   void clear() {
      this.slots.clear();
   }
}
