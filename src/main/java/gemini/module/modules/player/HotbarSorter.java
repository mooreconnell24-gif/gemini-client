package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.Inventories;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HotbarSorter extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay Ticks", "Ticks between slot swaps", 3.0, 1.0, 15.0, 1.0));
   private final BooleanSetting swordSlot1 = this.addSetting(new BooleanSetting("Sword Slot 1", "Keep sword in slot 1", true));
   private final BooleanSetting pickSlot2 = this.addSetting(new BooleanSetting("Pickaxe Slot 2", "Keep pickaxe in slot 2", true));
   private final BooleanSetting gappleSlot3 = this.addSetting(new BooleanSetting("Gapple Slot 3", "Keep Golden Apples in slot 3", true));
   private final BooleanSetting blocksSlot9 = this.addSetting(new BooleanSetting("Blocks Slot 9", "Keep building blocks in slot 9", true));
   private int wait;

   public HotbarSorter() {
      super("HotbarSorter", "Keep sword, pickaxe, gapples, and blocks sorted.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         if (this.swordSlot1.get() && this.ensureSlot(client, player, 0, s -> s.is(ItemTags.SWORDS))) {
            return;
         }

         if (this.pickSlot2.get() && this.ensureSlot(client, player, 1, s -> s.is(ItemTags.PICKAXES))) {
            return;
         }

         if (this.gappleSlot3.get() && this.ensureSlot(client, player, 2, s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE))) {
            return;
         }

         if (this.blocksSlot9.get()) {
            this.ensureSlot(client, player, 8, BlockPlacer::solidFiller);
         }
      }
   }

   private boolean ensureSlot(Minecraft client, LocalPlayer player, int targetHotbar, Predicate<ItemStack> matcher) {
      if (matcher.test(player.getInventory().getItem(targetHotbar))) {
         return false;
      } else {
         int found = Inventories.find(player, matcher, false);
         if (found >= 0 && found != targetHotbar) {
            client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(found), targetHotbar, ContainerInput.SWAP, player);
            return true;
         } else {
            return false;
         }
      }
   }
}
