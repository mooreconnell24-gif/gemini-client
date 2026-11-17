package gemini.module.modules.combat;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.util.ClientState;
import gemini.util.Inventories;
import gemini.util.SpCommands;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Puts a block into the space you stand in. It used to fake a jump with four extra position packets in one tick,
 * which the 26.x server answers by disconnecting the player ("Invalid move player packet"); in this single-player
 * client the block is placed by the integrated server instead, and one item is used up as a normal placement would.
 */
public final class Burrow extends Module {
   public Burrow() {
      super("Burrow", "Places obsidian, an ender chest or an anvil from your hotbar in the block you stand in, then turns off.", Category.COMBAT);
   }

   @Override
   public boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && player.onGround()) {
         BlockPos feet = player.blockPosition();
         Predicate<ItemStack> burrowBlock = CombatHelpers.obsidianOrResistant().or(s -> s.is(Items.ANVIL) || s.is(Items.CHIPPED_ANVIL));
         int slot = Inventories.findHotbar(player, burrowBlock);
         if (slot < 0) {
            CommandManager.feedback("§bBurrow §7needs obsidian, an ender chest or an anvil on your hotbar.");
         } else if (!client.level.getBlockState(feet).canBeReplaced()) {
            CommandManager.feedback("§bBurrow §7the block you stand in is not empty.");
         } else if (player.getInventory().getItem(slot).getItem() instanceof BlockItem item) {
            Block block = item.getBlock();
            SpCommands.onServer(server -> {
               if (server.level().getBlockState(feet).canBeReplaced() && server.level().setBlockAndUpdate(feet, block.defaultBlockState())) {
                  if (!server.hasInfiniteMaterials()) {
                     server.getInventory().getItem(slot).shrink(1);
                  }
               }
            });
         }
      }

      this.setEnabled(false, false);
   }
}
