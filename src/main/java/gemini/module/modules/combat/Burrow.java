package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.command.CommandManager;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.Inventories;
import gemini.util.SpCommands;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Burrow extends Module {
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Kept for old configs; the block is now set directly and needs no aiming", true));

   public Burrow() {
      super("Burrow", "Lag-place obsidian or ender chest inside your feet.", Category.COMBAT);
   }

   @Override
   public boolean restoresEnabledState() {
      return false;
   }

   /**
    * The classic burrow sends a burst of fake jump packets so the server lets a block be placed where the player
    * stands. Minecraft 26.3 disconnects for a second position packet in a tick, so the block is set through the
    * integrated server instead, and one item is taken from the hotbar stack in survival so it still costs a block.
    */
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
         } else if (player.getInventory().getItem(slot).getItem() instanceof BlockItem item) {
            String block = BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString();
            if (SpCommands.run("setblock " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + " " + block + " keep") && !player.isCreative()) {
               SpCommands.run("clear @s " + BuiltInRegistries.ITEM.getKey(item) + " 1");
            }
         }
      }

      this.setEnabled(false, false);
   }
}
