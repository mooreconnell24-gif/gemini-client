package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.util.BlockBreaking;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Clears plants around the player. Grass, ferns and flowers break in one hit, so they are punched with
 * {@code startDestroyBlock}; a cobweb takes several ticks even with a sword or shears, so it is kept as the current
 * target and mined over the following ticks with {@link BlockBreaking#keep()} until it is gone.
 */
public final class LawnMower extends Module {
   /** Ticks a cobweb may take before it is abandoned (shears need about 8, a sword about 8, a bare hand 80). */
   private static final int MAX_WEB_TICKS = 60;
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Clear radius around you in blocks", 4.0, 2.0, 5.5, 0.5));
   private final BooleanSetting flowers = this.addSetting(
      new BooleanSetting("Flowers", "Also clear flowers that break in one hit (leaves and other slow flowering blocks are left alone)", false)
   );
   private final BooleanSetting cobwebs = this.addSetting(
      new BooleanSetting("Cobwebs", "Mine cobwebs while you hold a sword or shears in your main hand", true)
   );
   private BlockPos web;
   private int webTicks;

   public LawnMower() {
      super("LawnMower", "Clear tall grass, ferns, dead bushes and fire (and optionally flowers and cobwebs) around you.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.stopWeb(true);
   }

   @Override
   public void onWorldChange() {
      this.web = null;
      this.webTicks = 0;
   }

   private void stopWeb(boolean abort) {
      Minecraft client = Minecraft.getInstance();
      if (this.web != null && abort && client.gameMode != null) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.web = null;
      this.webTicks = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client) || client.gui.screen() != null) {
         return;
      }

      LocalPlayer player = client.player;
      double reach = this.range.get();
      if (this.web != null) {
         if (this.cobwebs.get()
            && webTool(player.getMainHandItem())
            && client.level.getBlockState(this.web).is(Blocks.COBWEB)
            && BlockPlacer.inReach(player, this.web, reach)
            && ++this.webTicks <= MAX_WEB_TICKS) {
            BlockBreaking.keep();
            client.gameMode.continueDestroyBlock(this.web, Direction.UP);
            Swing.attack(player, InteractionHand.MAIN_HAND);
            return;
         }

         // Gone, out of reach, tool put away or taking too long: let the break lapse.
         this.stopWeb(!client.level.getBlockState(this.web).isAir());
      }

      boolean webs = this.cobwebs.get() && webTool(player.getMainHandItem());
      int r = (int)Math.ceil(reach);
      BlockPos c = player.blockPosition();
      for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 2, r))) {
         if (BlockPlacer.inReach(player, pos, reach)) {
            BlockState s = client.level.getBlockState(pos);
            if (webs && s.is(Blocks.COBWEB)) {
               this.web = pos.immutable();
               this.webTicks = 0;
               BlockBreaking.keep();
               client.gameMode.startDestroyBlock(this.web, Direction.UP);
               Swing.attack(player, InteractionHand.MAIN_HAND);
               return;
            }

            if (this.shouldClear(s) && s.getDestroyProgress(player, client.level, pos) >= 1.0F) {
               client.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
               Swing.attack(player, InteractionHand.MAIN_HAND);
               return;
            }
         }
      }
   }

   private static boolean webTool(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(Items.SHEARS);
   }

   private boolean shouldClear(BlockState s) {
      Block b = s.getBlock();
      if (b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH || b == Blocks.FIRE) {
         return true;
      } else {
         return this.flowers.get() && s.is(BlockTags.FLOWERS);
      }
   }
}
