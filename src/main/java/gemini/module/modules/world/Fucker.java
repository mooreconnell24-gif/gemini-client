package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Fucker extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Break reach through walls", 4.5, 1.5, 6.0, 0.1));
   private final BooleanSetting beds = this.addSetting(new BooleanSetting("Beds", "Break nearby beds", true));
   private final BooleanSetting eggs = this.addSetting(new BooleanSetting("Dragon Eggs", "Break dragon eggs", true));
   private final BooleanSetting cakes = this.addSetting(new BooleanSetting("Cakes", "Break cakes", false));

   public Fucker() {
      super("Fucker", "Break nearby Beds, Dragon Eggs, or Cakes through walls.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         int r = (int)Math.ceil(this.range.get());
         BlockPos c = player.blockPosition();

         for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
            if (BlockPlacer.inReach(player, pos, this.range.get()) && this.matches(client.level.getBlockState(pos))) {
               RotationManager.request(player, RotationManager.toward(player, pos), 90.0F, true, 5);
               client.gameMode.continueDestroyBlock(pos.immutable(), Direction.UP);
               player.swing(InteractionHand.MAIN_HAND);
               return;
            }
         }
      }
   }

   private boolean matches(BlockState state) {
      return this.beds.get() && state.is(BlockTags.BEDS) || this.eggs.get() && state.is(Blocks.DRAGON_EGG) || this.cakes.get() && state.is(Blocks.CAKE);
   }
}
