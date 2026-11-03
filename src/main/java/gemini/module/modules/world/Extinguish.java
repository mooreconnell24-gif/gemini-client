package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Extinguish extends Module {
   public Extinguish() {
      super("Extinguish", "Automatically punch out fire blocks around your feet.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null) {
         BlockPos c = client.player.blockPosition();

         for (BlockPos pos : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 2, 3))) {
            BlockState s = client.level.getBlockState(pos);
            if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
               client.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
               Swing.attack(client.player, InteractionHand.MAIN_HAND);
               return;
            }
         }
      }
   }
}
