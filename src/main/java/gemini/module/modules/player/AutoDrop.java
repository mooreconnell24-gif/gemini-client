package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class AutoDrop extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Blacklist drops the listed items", "Blacklist", "Blacklist", "Whitelist")
   );
   private final ListSetting items = this.addSetting(
      new ListSetting(
         "Items", "Items the mode applies to", ListSetting.Kind.ITEM, "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "string", "bone"
      )
   );
   private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Include Hotbar", "Also drop from the hotbar", false));
   private final BooleanSetting skipNamed = this.addSetting(new BooleanSetting("Skip Named", "Never throw items you renamed on an anvil", true));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between drops", 2.0, 0.0, 20.0, 1.0));
   private final NumberSetting perTick = this.addSetting(new NumberSetting("Per Tick", "How many stacks to throw in one burst", 1.0, 1.0, 9.0, 1.0));
   private int cooldown;

   public AutoDrop() {
      super("Auto Drop", "Throw unwanted items out of your inventory.", Category.PLAYER);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (PlayerHelpers.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            boolean blacklist = this.mode.is("Blacklist");
            int thrown = 0;

            for (int index = this.hotbar.get() ? 0 : 9; index < 36; index++) {
               ItemStack stack = player.getInventory().getItem(index);
               if (!stack.isEmpty() && this.items.items().contains(stack.getItem()) == blacklist && (!this.skipNamed.get() || stack.getCustomName() == null)
                  )
                {
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(index), 1, ContainerInput.THROW, player);
                  if (++thrown >= this.perTick.intValue()) {
                     this.cooldown = this.delay.intValue();
                     return;
                  }
               }
            }

            if (thrown > 0) {
               this.cooldown = this.delay.intValue();
            }
         }
      }
   }
}
