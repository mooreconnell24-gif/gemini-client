package gemini.util;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class ChunkScanner {
   private final Predicate<BlockState> test;
   private final int columnsPerTick;
   private final int maxResults;
   private final Long2ObjectOpenHashMap<long[]> found = new Long2ObjectOpenHashMap();
   private final Long2LongOpenHashMap scanned = new Long2LongOpenHashMap();
   private long[] ring = new long[0];
   private ClientLevel level;
   private int cursor;
   private int ringX = Integer.MIN_VALUE;
   private int ringZ = Integer.MIN_VALUE;
   private int ringRadius = -1;
   private long idleClock = -1L;
   private int total;

   public ChunkScanner(Predicate<BlockState> test, int columnsPerTick, int maxResults) {
      this.test = test;
      this.columnsPerTick = Math.max(1, columnsPerTick);
      this.maxResults = maxResults;
      this.scanned.defaultReturnValue(-1L);
   }

   public void clear() {
      this.found.clear();
      this.scanned.clear();
      this.ring = new long[0];
      this.level = null;
      this.cursor = 0;
      this.ringX = Integer.MIN_VALUE;
      this.ringZ = Integer.MIN_VALUE;
      this.ringRadius = -1;
      this.idleClock = -1L;
      this.total = 0;
   }

   public int count() {
      return this.total;
   }

   public void tick(ClientLevel level, int centerChunkX, int centerChunkZ, int radiusChunks) {
      if (level != this.level) {
         this.clear();
         this.level = level;
      }

      if (this.ringX != centerChunkX || this.ringZ != centerChunkZ || this.ringRadius != radiusChunks) {
         this.rebuildRing(centerChunkX, centerChunkZ, radiusChunks);
      }

      long clock = ChunkChanges.clock();
      if (this.ring.length != 0 && clock != this.idleClock) {
         int scans = 0;

         for (int steps = 0; steps < this.ring.length && scans < this.columnsPerTick; steps++) {
            if (this.cursor >= this.ring.length) {
               this.cursor = 0;
            }

            if (this.scanColumn(level, this.ring[this.cursor++])) {
               scans++;
            }
         }

         if (scans < this.columnsPerTick) {
            this.idleClock = clock;
         }
      }
   }

   private void rebuildRing(int cx, int cz, int radius) {
      this.ringX = cx;
      this.ringZ = cz;
      this.ringRadius = radius;
      this.idleClock = -1L;
      int side = radius * 2 + 1;
      long[] columns = new long[side * side];
      int n = 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            columns[n++] = ChunkPos.pack(cx + dx, cz + dz);
         }
      }

      Long[] boxed = new Long[n];

      for (int i = 0; i < n; i++) {
         boxed[i] = columns[i];
      }

      Arrays.sort(boxed, (a, b) -> Long.compare(distance(a, cx, cz), distance(b, cx, cz)));
      this.ring = new long[n];

      for (int i = 0; i < n; i++) {
         this.ring[i] = boxed[i];
      }

      this.cursor = 0;
      this.found.keySet().removeIf(key -> outside(key, cx, cz, radius));
      this.scanned.keySet().removeIf(key -> outside(key, cx, cz, radius));
      this.recount();
   }

   private static boolean outside(long packed, int cx, int cz, int radius) {
      return Math.abs(ChunkPos.getX(packed) - cx) > radius || Math.abs(ChunkPos.getZ(packed) - cz) > radius;
   }

   private static long distance(long packed, int cx, int cz) {
      long dx = ChunkPos.getX(packed) - cx;
      long dz = ChunkPos.getZ(packed) - cz;
      return dx * dx + dz * dz;
   }

   private void recount() {
      int sum = 0;
      ObjectIterator var2 = this.found.values().iterator();

      while (var2.hasNext()) {
         long[] positions = (long[])var2.next();
         sum += positions.length;
      }

      this.total = sum;
   }

   private boolean scanColumn(ClientLevel level, long packed) {
      int cx = ChunkPos.getX(packed);
      int cz = ChunkPos.getZ(packed);
      LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
      if (chunk == null) {
         long[] previous = (long[])this.found.remove(packed);
         if (previous != null) {
            this.total -= previous.length;
         }

         this.scanned.remove(packed);
         return false;
      } else {
         long stamp = ChunkChanges.stamp(packed);
         if (this.scanned.get(packed) == stamp) {
            return false;
         } else {
            LongArrayList hits = null;
            LevelChunkSection[] sections = chunk.getSections();
            int minY = chunk.getMinY();

            for (int index = 0; index < sections.length; index++) {
               LevelChunkSection section = sections[index];
               if (section != null && !section.hasOnlyAir() && section.maybeHas(this.test)) {
                  int baseY = minY + (index << 4);

                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           if (this.test.test(section.getBlockState(x, y, z))) {
                              if (hits == null) {
                                 hits = new LongArrayList();
                              }

                              hits.add(BlockPos.asLong((cx << 4) + x, baseY + y, (cz << 4) + z));
                           }
                        }
                     }
                  }
               }
            }

            long[] previous = hits == null ? (long[])this.found.remove(packed) : (long[])this.found.put(packed, hits.toLongArray());
            int delta = (hits == null ? 0 : hits.size()) - (previous == null ? 0 : previous.length);
            this.total += delta;
            this.scanned.put(packed, stamp);
            return true;
         }
      }
   }

   public void forEach(ChunkScanner.ColumnFilter columns, LongConsumer visitor) {
      int budget = this.maxResults;

      for (long column : this.ring) {
         long[] positions = (long[])this.found.get(column);
         if (positions != null && columns.test(ChunkPos.getX(column), ChunkPos.getZ(column))) {
            for (long position : positions) {
               if (budget-- <= 0) {
                  return;
               }

               visitor.accept(position);
            }
         }
      }
   }

   @FunctionalInterface
   public interface ColumnFilter {
      boolean test(int var1, int var2);
   }
}
