package gemini.util;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Unload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AfterClientLevelChange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public final class ChunkChanges {
   private static final Long2LongOpenHashMap STAMPS = new Long2LongOpenHashMap();
   private static long clock = 1L;

   private ChunkChanges() {
   }

   public static void init() {
      ClientChunkEvents.CHUNK_LOAD.register((Load)(level, chunk) -> touch(chunk.getPos().pack()));
      ClientChunkEvents.CHUNK_UNLOAD.register((Unload)(level, chunk) -> {
         STAMPS.remove(chunk.getPos().pack());
         clock++;
      });
      ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((AfterClientLevelChange)(client, level) -> {
         STAMPS.clear();
         clock++;
      });
   }

   public static void blockChanged(BlockPos pos) {
      touch(ChunkPos.pack(pos));
   }

   private static void touch(long column) {
      STAMPS.put(column, ++clock);
   }

   public static long stamp(long column) {
      return STAMPS.get(column);
   }

   public static long clock() {
      return clock;
   }
}
