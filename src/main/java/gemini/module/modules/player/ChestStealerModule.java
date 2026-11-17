package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

public final class ChestStealerModule extends Module {
   private final NumberSetting startDelay = this.addSetting(
      new NumberSetting("Start Delay", "Ticks to wait after the container opens before taking anything", 2.0, 0.0, 20.0, 1.0)
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between items; 0 takes everything at once", 1.0, 0.0, 10.0, 1.0));
   private final ModeSetting filter = this.addSetting(
      new ModeSetting("Filter", "All takes everything; Only Listed takes just the listed items; Except Listed leaves the listed items behind", "All", "All", "Only Listed", "Except Listed")
   );
   private final ListSetting items = this.addSetting(
      new ListSetting("Items", "Items the filter applies to", ListSetting.Kind.ITEM).showIf(() -> !this.filter.is("All"))
   );
   private final BooleanSetting autoClose = this.addSetting(new BooleanSetting("Auto Close", "Close the container once there is nothing left to take", true));
   private int cooldown;
   /** The menu the start delay was already counted for, so it runs once per opened container. */
   private AbstractContainerMenu started;

   public ChestStealerModule() {
      super("Chest Stealer", "Takes everything, or only the items you choose, out of the chest, barrel or shulker box you open.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Chest Steal");
      names.add("Auto Loot");
      return names;
   }

   @Override
   public void onDisable() {
      this.started = null;
      this.cooldown = 0;
   }

   @Override
   public void onWorldChange() {
      this.started = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null) {
         return;
      }

      AbstractContainerMenu menu = player.containerMenu;
      if (!(menu instanceof ChestMenu) && !(menu instanceof ShulkerBoxMenu)) {
         this.started = null;
         this.cooldown = 0;
         return;
      }

      if (menu != this.started) {
         this.started = menu;
         this.cooldown = this.startDelay.intValue();
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      boolean filtered = !this.filter.is("All");
      boolean onlyListed = this.filter.is("Only Listed");
      Set<Item> listed = filtered ? this.items.items() : Set.of();
      boolean anyWanted = false;
      for (Slot slot : menu.slots) {
         if (slot.container == player.getInventory() || !slot.hasItem() || filtered && listed.contains(slot.getItem().getItem()) != onlyListed) {
            continue;
         }

         anyWanted = true;
         if (player.getInventory().getFreeSlot() < 0 && player.getInventory().getSlotWithRemainingSpace(slot.getItem()) < 0) {
            // No room for this stack; a later one may still fit onto a partial stack.
            continue;
         }

         client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
         this.cooldown = this.delay.intValue();
         if (this.cooldown > 0) {
            return;
         }
      }

      if (!anyWanted && this.autoClose.get()) {
         player.closeContainer();
      }
   }
}
