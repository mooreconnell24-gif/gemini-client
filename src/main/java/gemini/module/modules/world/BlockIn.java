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
import net.minecraft.world.item.ItemStack;

public final class BlockIn extends PlacementModuleBase {
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   public BlockIn() {
      super("BlockIn", "Rapidly build a full 1x2 protective shell around yourself.", Category.WORLD, 0, 4, 4.5);
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      BlockPos feet = player.blockPosition();
      for (Direction dir : SIDES) {
         targets.add(feet.relative(dir));
         targets.add(feet.above().relative(dir));
      }

      targets.add(feet.above(2));
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return BlockPlacer::solidFiller;
   }
}
