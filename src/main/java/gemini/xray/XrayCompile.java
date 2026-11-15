package gemini.xray;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class XrayCompile {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final Direction[] DIRECTIONS = Direction.values();
   private static final ThreadLocal<XrayCompile.Collector> COLLECTOR = ThreadLocal.withInitial(XrayCompile.Collector::new);

   private XrayCompile() {
   }

   public static void begin(SectionPos sectionPos) {
      XrayCompile.Collector collector = COLLECTOR.get();
      collector.config = XrayConfig.current();
      collector.recording = collector.config != null;
      if (collector.recording) {
         collector.section = sectionPos.asLong();
         collector.positions.clear();
      }
   }

   public static void end() {
      XrayCompile.Collector collector = COLLECTOR.get();
      if (collector.recording) {
         collector.recording = false;
         XrayData.commit(collector.config, collector.section, collector.positions);
         collector.positions.clear();
         collector.config = null;
      }
   }

   public static BlockState filter(XrayConfig config, BlockAndTintGetter region, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return state;
      } else if (config.isTarget(state)) {
         XrayCompile.Collector collector = COLLECTOR.get();
         if (config.hides() && config.exposedOnly() && !exposed(region, pos, collector.cursor)) {
            return AIR;
         } else {
            if (collector.recording) {
               collector.positions.add(pos.asLong());
            }

            return state;
         }
      } else {
         return !config.isVisible(state) && config.hides() ? AIR : state;
      }
   }

   private static boolean exposed(BlockAndTintGetter region, BlockPos pos, MutableBlockPos cursor) {
      for (Direction direction : DIRECTIONS) {
         cursor.setWithOffset(pos, direction);
         if (!region.getBlockState(cursor).isSolidRender()) {
            return true;
         }
      }

      return false;
   }

   private static final class Collector {
      private final LongArrayList positions = new LongArrayList();
      private final MutableBlockPos cursor = new MutableBlockPos();
      private long section;
      private boolean recording;
      /** The configuration this section is being compiled under; its positions are only kept if it is still current at the end. */
      private XrayConfig config;
   }
}
