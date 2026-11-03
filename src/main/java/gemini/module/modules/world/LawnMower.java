package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class LawnMower extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Clear radius around player", 4.0, 2.0, 5.5, 0.5));
   private final BooleanSetting flowers = this.addSetting(new BooleanSetting("Flowers", "Also clear flowers", false));
   private final BooleanSetting cobwebs = this.addSetting(new BooleanSetting("Cobwebs", "Break cobwebs when holding a sword or shears", true));

   public LawnMower() {
      super("LawnMower", "Clear tall grass, ferns, and cobwebs around you.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         int r = (int)Math.ceil(this.range.get());
         BlockPos c = player.blockPosition();

         for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 2, r))) {
            if (BlockPlacer.inReach(player, pos, this.range.get())) {
               BlockState s = client.level.getBlockState(pos);
               if (this.shouldClear(s)) {
                  client.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
                  player.swing(InteractionHand.MAIN_HAND);
                  return;
               }
            }
         }
      }
   }

   private boolean shouldClear(BlockState s) {
      Block b = s.getBlock();
      if (b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH || b == Blocks.FIRE) {
         return true;
      } else {
         return this.flowers.get() && s.is(BlockTags.FLOWERS) ? true : this.cobwebs.get() && b == Blocks.COBWEB;
      }
   }
}
