package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.util.BlockBreaking;
import gemini.module.Category;
import gemini.event.ActionPriority;
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
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block to break, in blocks, walls included", 4.5, 1.5, 6.0, 0.1));
   private final BooleanSetting beds = this.addSetting(new BooleanSetting("Beds", "Break nearby beds", true));
   private final BooleanSetting eggs = this.addSetting(new BooleanSetting("Dragon Eggs", "Break dragon eggs", true));
   private final BooleanSetting cakes = this.addSetting(new BooleanSetting("Cakes", "Break cakes", false));

   private BlockPos target;

   public Fucker() {
      super("Fucker", "Breaks nearby beds, dragon eggs or cakes, even through walls.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.target != null && client.gameMode != null) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.target = null;
   }

   @Override
   public void onWorldChange() {
      this.target = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         double reach = this.range.get();
         // Stay on the block being broken while it still qualifies, so the scan (up to 13x13x13 reads) only runs
         // when there is nothing to break and the damage is never thrown away by hopping to another block.
         if (this.target == null || !BlockPlacer.inReach(player, this.target, reach) || !this.matches(client.level.getBlockState(this.target))) {
            this.target = this.find(client, player, reach);
         }

         if (this.target != null) {
            RotationManager.request(player, RotationManager.toward(player, this.target), 90.0F, true, ActionPriority.WORLD_UTILITY);
            BlockBreaking.keep();
            client.gameMode.continueDestroyBlock(this.target, Direction.UP);
            Swing.attack(player, InteractionHand.MAIN_HAND);
         }
      }
   }

   private BlockPos find(Minecraft client, LocalPlayer player, double reach) {
      int r = (int)Math.ceil(reach);
      BlockPos c = player.blockPosition();
      for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
         if (BlockPlacer.inReach(player, pos, reach) && this.matches(client.level.getBlockState(pos))) {
            return pos.immutable();
         }
      }

      return null;
   }

   private boolean matches(BlockState state) {
      return this.beds.get() && state.is(BlockTags.BEDS) || this.eggs.get() && state.is(Blocks.DRAGON_EGG) || this.cakes.get() && state.is(Blocks.CAKE);
   }
}
