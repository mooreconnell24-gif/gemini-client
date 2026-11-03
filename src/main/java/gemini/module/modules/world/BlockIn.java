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

public final class BlockIn extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 0, 4, 4.5);

   public BlockIn() {
      super("BlockIn", "Rapidly build a full 1x2 protective shell around yourself.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null && this.placer.ready()) {
         BlockPos feet = client.player.blockPosition();
         List<BlockPos> shell = new ArrayList<>(10);

         for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            shell.add(feet.relative(dir));
            shell.add(feet.above().relative(dir));
         }

         shell.add(feet.above(2));
         this.placer.place(client, shell, BlockPlacer::solidFiller);
      }
   }
}
