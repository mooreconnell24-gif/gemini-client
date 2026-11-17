package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.OffhandClaim;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class AutoMend extends Module {
   /** The inventory menu's offhand slot. */
   private static final int OFFHAND_MENU_SLOT = 45;
   private final NumberSetting threshold = this.addSetting(
      new NumberSetting("Repair Below %", "Repair items below this durability (percent)", 90.0, 5.0, 100.0, 1.0)
   );
   private final BooleanSetting includeArmor = this.addSetting(
      new BooleanSetting("Include Armor", "Also take off worn armour to mend it in the offhand, and put it back on once repaired", false)
   );
   private final BooleanSetting restore = this.addSetting(
      new BooleanSetting("Restore Offhand", "Put the original offhand item back once everything is repaired", true)
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between swaps", 10.0, 1.0, 60.0, 1.0));
   private int originalSlot = -1;
   private boolean swapped;
   /** Armor pieces this module took off, by armor index (0 feet .. 3 head); they go back on once repaired. */
   private final boolean[] pulledArmor = new boolean[4];
   private int wait;

   public AutoMend() {
      super("Auto Mend", "Moves damaged Mending gear into your offhand so experience repairs it.", Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.swapped = false;
      this.originalSlot = -1;
      Arrays.fill(this.pulledArmor, false);
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && Inventories.canClick(player)) {
         // Armor first: the offhand may hold a piece that was taken off, and the restore below would bury it.
         this.reequipArmor(client, player, true);
         if (this.restore.get() && this.swapped && this.originalSlot >= 0) {
            Inventories.swapToOffhand(client, player, this.originalSlot);
         }

         this.reequipArmor(client, player, true);
      }

      this.swapped = false;
      this.originalSlot = -1;
      Arrays.fill(this.pulledArmor, false);
   }

   /** The swapped slots and pulled armor belong to the player that just left. */
   @Override
   public void onWorldChange() {
      this.swapped = false;
      this.originalSlot = -1;
      Arrays.fill(this.pulledArmor, false);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && this.wait-- <= 0 && Inventories.canClick(client.player) && !OffhandClaim.totemOwns(client.player)) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         if (this.reequipArmor(client, player, false)) {
            return;
         }

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
                  if (Inventories.swapToOffhand(client, player, this.originalSlot)) {
                     this.swapped = false;
                  }
               } else if (!this.restore.get()) {
                  this.swapped = false;
               }
            } else if (slot >= 36) {
               // Off into the inventory first; the next pass swaps it into the offhand like any other item.
               client.gameMode.handleContainerInput(player.inventoryMenu.containerId, 8 - (slot - 36), 0, ContainerInput.QUICK_MOVE, player);
               if (player.getInventory().getItem(slot).isEmpty()) {
                  this.pulledArmor[slot - 36] = true;
               }
            } else {
               boolean first = !this.swapped;
               if (Inventories.swapToOffhand(client, player, slot) && first) {
                  this.originalSlot = slot;
                  this.swapped = true;
               }
            }
         }
      }
   }

   /**
    * Puts armor this module took off back on: a piece in the offhand once it is fully repaired (or always when
    * {@code force}), and with {@code force} also pieces still waiting in the inventory. Returns true when it clicked.
    */
   private boolean reequipArmor(Minecraft client, LocalPlayer player, boolean force) {
      boolean clicked = false;
      for (int armor = 0; armor < 4; armor++) {
         if (!this.pulledArmor[armor]) {
            continue;
         }

         if (!player.getInventory().getItem(36 + armor).isEmpty()) {
            // Something else was put on meanwhile; the piece stays where it is.
            this.pulledArmor[armor] = false;
            continue;
         }

         ItemStack offhand = player.getOffhandItem();
         if (armorIndex(offhand) == armor && (force || !offhand.isDamaged())) {
            if (Hands.claimOffhand()) {
               client.gameMode.handleContainerInput(player.inventoryMenu.containerId, OFFHAND_MENU_SLOT, 0, ContainerInput.QUICK_MOVE, player);
               this.pulledArmor[armor] = false;
               clicked = true;
            }

            continue;
         }

         if (force) {
            for (int index = 0; index < 36; index++) {
               ItemStack stack = player.getInventory().getItem(index);
               if (armorIndex(stack) == armor && PlayerHelpers.mending(stack)) {
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(index), 0, ContainerInput.QUICK_MOVE, player);
                  this.pulledArmor[armor] = false;
                  clicked = true;
                  break;
               }
            }
         }
      }

      return clicked;
   }

   /** The armor index (0 feet .. 3 head) the stack is worn in, or -1 for anything that is not humanoid armor. */
   private static int armorIndex(ItemStack stack) {
      Equippable equippable = stack.isEmpty() ? null : stack.get(DataComponents.EQUIPPABLE);
      if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
         return -1;
      }

      return equippable.slot().getIndex();
   }

   private static double durability(ItemStack stack) {
      return stack.getMaxDamage() <= 0 ? 100.0 : 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
   }

   @Override
   public String info() {
      return this.swapped ? "mending" : null;
   }
}
