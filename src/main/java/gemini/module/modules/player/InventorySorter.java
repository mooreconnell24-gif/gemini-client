package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Sorts the main inventory by item id (or by stack size), one swap per step so the server keeps up. */
public final class InventorySorter extends Module {
   private final ModeSetting order = this.addSetting(new ModeSetting("Order", "How stacks are ordered", "Item", "Item", "Count"));
   private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Include Hotbar", "Also sort the nine hotbar slots", false));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between moves", 1.0, 0.0, 10.0, 1.0));
   private int cooldown;
   private int moves;
   private int guard;

   public InventorySorter() {
      super("Inventory Sorter", "Tidy your inventory into sorted, merged stacks.", Category.PLAYER);
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      this.cooldown = 0;
      this.moves = 0;
      this.guard = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null) {
         return;
      }

      if (client.gui.screen() != null && !(client.gui.screen() instanceof InventoryScreen) || !Inventories.inventoryOpenOrClosed(player)) {
         return;
      }

      if (!player.inventoryMenu.getCarried().isEmpty()) {
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      if (++this.guard > 400) {
         CommandManager.feedback("§bInventory Sorter §7stopped: the inventory kept changing.");
         this.setEnabled(false, false);
         return;
      }

      List<Integer> slots = new ArrayList<>();
      for (int i = this.hotbar.get() ? 0 : 9; i < 36; i++) {
         slots.add(i);
      }

      List<ItemStack> current = new ArrayList<>();
      for (int index : slots) {
         current.add(player.getInventory().getItem(index));
      }

      List<ItemStack> wanted = new ArrayList<>();
      for (ItemStack stack : current) {
         if (!stack.isEmpty()) {
            wanted.add(stack);
         }
      }

      Comparator<ItemStack> byId = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      Comparator<ItemStack> byCount = Comparator.<ItemStack>comparingInt(ItemStack::getCount).reversed();
      wanted.sort(this.order.is("Count") ? byCount.thenComparing(byId) : byId.thenComparing(byCount));
      for (int i = 0; i < slots.size(); i++) {
         ItemStack want = i < wanted.size() ? wanted.get(i) : ItemStack.EMPTY;
         ItemStack have = current.get(i);
         if (same(have, want)) {
            continue;
         }

         int from = -1;
         for (int j = i + 1; j < slots.size(); j++) {
            if (!want.isEmpty() && same(current.get(j), want)) {
               from = j;
               break;
            }
         }

         if (from < 0) {
            continue;
         }

         int container = player.inventoryMenu.containerId;
         int source = Inventories.menuSlot(slots.get(from));
         int target = Inventories.menuSlot(slots.get(i));
         client.gameMode.handleContainerInput(container, source, 0, ContainerInput.PICKUP, player);
         client.gameMode.handleContainerInput(container, target, 0, ContainerInput.PICKUP, player);
         if (!player.inventoryMenu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(container, source, 0, ContainerInput.PICKUP, player);
         }

         this.moves++;
         this.cooldown = this.delay.intValue();
         return;
      }

      CommandManager.feedback("§bInventory Sorter §7done" + (this.moves == 0 ? ", already tidy." : " in " + this.moves + " moves."));
      this.setEnabled(false, false);
   }

   private static boolean same(ItemStack a, ItemStack b) {
      if (a.isEmpty() || b.isEmpty()) {
         return a.isEmpty() && b.isEmpty();
      } else {
         return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
      }
   }

   @Override
   public String info() {
      return this.moves > 0 ? this.moves + " moves" : null;
   }
}
