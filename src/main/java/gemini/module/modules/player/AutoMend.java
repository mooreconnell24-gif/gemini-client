package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.OffhandClaim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class AutoMend extends Module {
   private final NumberSetting threshold = this.addSetting(
      new NumberSetting("Repair Below %", "Start repairing an item under this durability", 90.0, 5.0, 100.0, 1.0)
   );
   private final BooleanSetting includeArmor = this.addSetting(
      new BooleanSetting("Include Armor", "Also pull worn armor into the offhand while it mends", false)
   );
   private final BooleanSetting restore = this.addSetting(
      new BooleanSetting("Restore Offhand", "Put the original offhand item back when everything is repaired", true)
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between swaps", 10.0, 1.0, 60.0, 1.0));
   private ItemStack original = ItemStack.EMPTY;
   private int originalSlot = -1;
   private boolean swapped;
   private int wait;

   public AutoMend() {
      super("Auto Mend", "Swap damaged Mending gear to offhand to repair.", Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.swapped = false;
      this.original = ItemStack.EMPTY;
      this.originalSlot = -1;
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.restore.get() && this.swapped && ClientState.ready(client) && this.originalSlot >= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
         Inventories.swapToOffhand(client, client.player, this.originalSlot);
      }

      this.swapped = false;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player) && !OffhandClaim.totemOwns(client.player)) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         ItemStack offhand = player.getOffhandItem();
         if (!PlayerHelpers.mending(offhand) || !offhand.isDamaged()) {
            int slot = Inventories.find(player, stackx -> PlayerHelpers.mending(stackx) && durability(stackx) < this.threshold.get(), false);
            if (slot < 0 && this.includeArmor.get()) {
               for (int armor = 36; armor < 40; armor++) {
                  ItemStack stack = player.getInventory().getItem(armor);
                  if (PlayerHelpers.mending(stack) && durability(stack) < this.threshold.get()) {
                     slot = armor;
                     break;
                  }
               }
            }

            if (slot < 0) {
               if (this.swapped && this.restore.get() && this.originalSlot >= 0) {
                  Inventories.swapToOffhand(client, player, this.originalSlot);
                  this.swapped = false;
               }
            } else if (slot >= 36) {
               client.gameMode.handleContainerInput(player.inventoryMenu.containerId, 8 - (slot - 36), 0, ContainerInput.QUICK_MOVE, player);
            } else {
               if (!this.swapped) {
                  this.original = offhand.copy();
                  this.originalSlot = slot;
                  this.swapped = true;
               }

               Inventories.swapToOffhand(client, player, slot);
            }
         }
      }
   }

   private static double durability(ItemStack stack) {
      return stack.getMaxDamage() <= 0 ? 100.0 : 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
   }

   @Override
   public String info() {
      return this.swapped ? "mending" : null;
   }
}
