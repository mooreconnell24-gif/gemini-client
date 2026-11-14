package gemini.util.pathing;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Arrays;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Fast block reads for the pathfinder.
 *
 * <p>A search reads hundreds of thousands of blocks, almost all of them in the same few chunks. Going through
 * {@code Level.getBlockState} pays for a chunk lookup on every read; this view keeps the chunks it has already
 * found in a small direct-mapped cache and reads the section palette directly. What the pathfinder needs to know
 * about a block state (open, lava, water, climbable, floor, hot, falling) is worked out once per state and kept as
 * bit flags, so the common question "can I walk through this?" is one map lookup.
 *
 * <p><b>Unloaded chunks.</b> Vanilla answers reads in an unloaded chunk with air, which makes the unknown look like
 * an open void to bridge across. Here they read as an unbreakable wall, and {@link #touchedUnloaded()} tells the
 * caller that the search ran into the edge of the loaded world, so it can wait for chunks instead of giving up.
 *
 * <p>Call {@link #begin(Level)} before each batch of reads (once per tick or search); cached chunks are only
 * trusted until the next call. Client thread only.
 */
public final class BlockView {
   public static final int AIR = 1;
   /** Not lava and nothing to collide with. */
   public static final int PASSABLE = 1 << 1;
   public static final int LAVA = 1 << 2;
   public static final int WATER = 1 << 3;
   public static final int CLIMBABLE = 1 << 4;
   /** Has a collision shape, so it can be stood on. */
   public static final int FLOOR = 1 << 5;
   /** Fire, soul fire or magma: never stand on it. */
   public static final int HOT = 1 << 6;
   public static final int FALLING = 1 << 7;
   /** The collision shape depends on more than the state, so {@link #PASSABLE} and {@link #FLOOR} must be asked live. */
   public static final int DYNAMIC = 1 << 8;
   public static final int LIQUID = 1 << 9;
   private static final int COMPUTED = 1 << 30;
   private static final int SLOTS = 64;
   private static final BlockState AIR_STATE = Blocks.AIR.defaultBlockState();
   /** What an unloaded chunk reads as: solid and unbreakable. */
   private static final BlockState UNLOADED = Blocks.BARRIER.defaultBlockState();
   private final Reference2IntOpenHashMap<BlockState> flags = new Reference2IntOpenHashMap<>();
   private final long[] slotKey = new long[SLOTS];
   private final int[] slotStamp = new int[SLOTS];
   private final LevelChunk[] slotChunk = new LevelChunk[SLOTS];
   private final MutableBlockPos scratch = new MutableBlockPos();
   private Level level;
   private int stamp = 1;
   private int minY;
   private int maxY;
   private boolean touchedUnloaded;

   /** Starts a batch of reads: forgets cached chunks (they may have unloaded) and clears {@link #touchedUnloaded()}. */
   public void begin(Level level) {
      if (level != this.level) {
         // Tags can differ between worlds, and a held level would keep a closed world alive.
         this.flags.clear();
         Arrays.fill(this.slotChunk, null);
         this.level = level;
      }

      this.stamp++;
      this.touchedUnloaded = false;
      if (level != null) {
         this.minY = level.getMinY();
         this.maxY = this.minY + level.getHeight();
      }
   }

   /** Drops every reference to the world; call when the bot stops. */
   public void release() {
      this.begin(null);
   }

   public Level level() {
      return this.level;
   }

   /** True when a read since {@link #begin} landed in a chunk that is not loaded. */
   public boolean touchedUnloaded() {
      return this.touchedUnloaded;
   }

   public boolean loaded(int x, int z) {
      return this.chunk(x >> 4, z >> 4) != null;
   }

   private LevelChunk chunk(int cx, int cz) {
      long key = (long)cx << 32 | cz & 0xFFFFFFFFL;
      int slot = (cx * 31 + cz) & SLOTS - 1;
      if (this.slotStamp[slot] == this.stamp && this.slotKey[slot] == key) {
         return this.slotChunk[slot];
      }

      LevelChunk chunk = this.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk loaded && !loaded.isEmpty()
         ? loaded
         : null;
      this.slotStamp[slot] = this.stamp;
      this.slotKey[slot] = key;
      this.slotChunk[slot] = chunk;
      return chunk;
   }

   public BlockState state(int x, int y, int z) {
      if (y < this.minY || y >= this.maxY) {
         return AIR_STATE;
      }

      LevelChunk chunk = this.chunk(x >> 4, z >> 4);
      if (chunk == null) {
         this.touchedUnloaded = true;
         return UNLOADED;
      }

      LevelChunkSection section = chunk.getSections()[y - this.minY >> 4];
      return section == null || section.hasOnlyAir() ? AIR_STATE : section.getBlockState(x & 15, y & 15, z & 15);
   }

   /** The flag bits of a state; the position is only used the first time a state is seen. */
   public int flags(BlockState state, int x, int y, int z) {
      int cached = this.flags.getInt(state);
      if (cached != 0) {
         return cached;
      }

      int bits = COMPUTED;
      boolean lava = state.getFluidState().is(FluidTags.LAVA);
      if (state.isAir()) {
         bits |= AIR;
      }

      if (lava) {
         bits |= LAVA;
      }

      if (state.getFluidState().is(FluidTags.WATER)) {
         bits |= WATER;
      }

      if (state.is(BlockTags.CLIMBABLE)) {
         bits |= CLIMBABLE;
      }

      if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)) {
         bits |= HOT;
      }

      if (state.getBlock() instanceof FallingBlock) {
         bits |= FALLING;
      }

      if (state.getBlock() instanceof LiquidBlock) {
         bits |= LIQUID;
      }

      if (!state.isAir() && state.getBlock().hasDynamicShape()) {
         bits |= DYNAMIC;
      } else {
         boolean empty = state.isAir() || state.getCollisionShape(this.level, this.scratch.set(x, y, z)).isEmpty();
         if (empty && !lava) {
            bits |= PASSABLE;
         }

         if (!empty) {
            bits |= FLOOR;
         }
      }

      this.flags.put(state, bits);
      return bits;
   }

   /** Open to walk through: not lava, and air or without a collision shape. */
   public boolean passable(BlockState state, int x, int y, int z) {
      int bits = this.flags(state, x, y, z);
      if ((bits & DYNAMIC) == 0) {
         return (bits & PASSABLE) != 0;
      }

      return (bits & LAVA) == 0 && state.getCollisionShape(this.level, this.scratch.set(x, y, z)).isEmpty();
   }

   /** Solid enough to stand on. */
   public boolean floor(BlockState state, int x, int y, int z) {
      int bits = this.flags(state, x, y, z);
      if ((bits & DYNAMIC) == 0) {
         return (bits & FLOOR) != 0;
      }

      return !state.getCollisionShape(this.level, this.scratch.set(x, y, z)).isEmpty();
   }

   public boolean is(BlockState state, int x, int y, int z, int flag) {
      return (this.flags(state, x, y, z) & flag) != 0;
   }
}
