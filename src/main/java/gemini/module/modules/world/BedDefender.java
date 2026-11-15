package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.util.BlockPlacer;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;

public final class BedDefender extends PlacementModuleBase {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   public BedDefender() {
      super("BedDefender", "Surround nearby beds with defensive hotbar blocks.", Category.WORLD, 1, 3, 4.5);
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      BlockPos center = player.blockPosition();
      // betweenClosed reuses one mutable position, so every position kept must be copied out of it.
      for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
         if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
            targets.add(pos.above());
            for (Direction dir : SIDES) {
               targets.add(pos.relative(dir));
            }
         }
      }
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return BlockPlacer::solidFiller;
   }
}
