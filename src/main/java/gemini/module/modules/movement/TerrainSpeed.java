package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TerrainSpeed extends Module {
   private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Speed multiplier on ice, soul sand, and stairs", 1.35, 1.05, 2.5, 0.05));

   public TerrainSpeed() {
      super("TerrainSpeed", "Move faster across ice, soul sand, and stairs.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client) && client.player.onGround() && MoveUtil.hasInput(client.player)) {
         BlockState below = client.level.getBlockState(client.player.blockPosition().below());
         if (below.is(BlockTags.ICE) || below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL) || below.is(BlockTags.STAIRS)) {
            MoveUtil.setHorizontal(client.player, MoveUtil.baseSpeed(client.player) * this.boost.get());
         }
      }
   }
}
