package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class AntiBreak extends Module {
   private final NumberSetting durability = this.addSetting(
      new NumberSetting("Durability", "Protect items with this many uses left or fewer", 10.0, 1.0, 100.0, 1.0)
   );
   private final BooleanSetting tools = this.addSetting(new BooleanSetting("Held Items", "Move a nearly broken held item out of the hotbar", true));
   private final BooleanSetting armor = this.addSetting(new BooleanSetting("Armor", "Take off armor that is about to break", true));
   private final BooleanSetting notify = this.addSetting(new BooleanSetting("Notify", "Say which item was saved", true));

   public AntiBreak() {
      super("Anti Break", "Stop using items that are one hit from breaking.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && Inventories.inventoryOpenOrClosed(client.player)) {
         LocalPlayer player = client.player;
         if (this.tools.get()) {
            ItemStack held = player.getMainHandItem();
            if (this.atRisk(held)) {
               int target = emptySlot(player);
               if (target >= 0) {
                  int selected = player.getInventory().getSelectedSlot();
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(selected), 0, ContainerInput.PICKUP, player);
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(target), 0, ContainerInput.PICKUP, player);
                  this.report(held);
                  return;
               }
            }
         }

         if (this.armor.get()) {
            for (int index = 36; index < 40; index++) {
               ItemStack worn = player.getInventory().getItem(index);
               if (this.atRisk(worn)) {
                  int target = emptySlot(player);
                  if (target >= 0) {
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, 8 - (index - 36), 0, ContainerInput.QUICK_MOVE, player);
                     this.report(worn);
                     return;
                  }
               }
            }
         }
      }
   }

   private boolean atRisk(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= this.durability.intValue();
   }

   private static int emptySlot(LocalPlayer player) {
      for (int index = 9; index < 36; index++) {
         if (player.getInventory().getItem(index).isEmpty()) {
            return index;
         }
      }

      return -1;
   }

   private void report(ItemStack stack) {
      if (this.notify.get()) {
         CommandManager.feedback("§bAnti Break §7saved §f" + stack.getHoverName().getString() + "§7.");
      }
   }
}
