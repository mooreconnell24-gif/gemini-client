package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InventoryCleaner extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay Ticks", "Ticks between inventory clean actions", 3.0, 1.0, 20.0, 1.0));
   private final BooleanSetting dropJunk = this.addSetting(new BooleanSetting("Drop Junk", "Drop rotten flesh, seeds, string, bowls and other junk", true));
   private int wait;

   public InventoryCleaner() {
      super("InventoryCleaner", "Clean junk items and organize your hotbar.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         if (this.dropJunk.get()) {
            for (int i = 9; i < 36; i++) {
               ItemStack stack = player.getInventory().getItem(i);
               if (isJunk(stack)) {
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(i), 1, ContainerInput.THROW, player);
                  return;
               }
            }
         }

         if (!player.getInventory().getItem(0).is(ItemTags.SWORDS)) {
            int sword = Inventories.find(player, s -> s.is(ItemTags.SWORDS), false);
            if (sword > 0) {
               client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(sword), 0, ContainerInput.SWAP, player);
            }
         }
      }
   }

   private static boolean isJunk(ItemStack s) {
      if (s.isEmpty() || s.isEnchanted() || s.has(DataComponents.CUSTOM_NAME)) {
         return false;
      }
      return s.is(Items.ROTTEN_FLESH)
         || s.is(Items.POISONOUS_POTATO)
         || s.is(Items.WHEAT_SEEDS)
         || s.is(Items.BEETROOT_SEEDS)
         || s.is(Items.DEAD_BUSH)
         || s.is(Items.BOWL)
         || s.is(Items.STRING)
         || s.is(Items.SPIDER_EYE)
         || s.is(Items.FEATHER)
         || s.is(Items.FLINT);
   }
}
