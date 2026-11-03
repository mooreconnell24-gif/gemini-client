package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.util.BlockPlacer;
import gemini.util.PlaceSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;

public final class BedDefender extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.5);

   public BedDefender() {
      super("BedDefender", "Surround nearby beds with defensive hotbar blocks.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null && this.placer.ready()) {
         BlockPos c = client.player.blockPosition();
         List<BlockPos> targets = new ArrayList<>();

         for (BlockPos pos : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
               targets.add(pos.above());

               for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                  targets.add(pos.relative(dir));
               }
            }
         }

         if (!targets.isEmpty()) {
            this.placer.place(client, targets, BlockPlacer::solidFiller);
         }
      }
   }
}
