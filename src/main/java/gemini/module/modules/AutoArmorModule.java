package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.item.equipment.Equippable;

public final class AutoArmorModule extends Module {
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final int ARMOR_MENU_START = 5;
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between equip actions", 3.0, 0.0, 20.0, 1.0));
   private final BooleanSetting keepElytra = this.addSetting(new BooleanSetting("Keep Elytra", "Never swap your elytra for a chestplate", true));
   private final BooleanSetting onlyInInventory = this.addSetting(new BooleanSetting("Only In Inventory", "Wait until your inventory screen is open", false));
   private int cooldown;

   public AutoArmorModule() {
      super("AutoArmor", "Equip the best armor from your inventory.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null && Inventories.inventoryOpenOrClosed(player) && !player.isDeadOrDying()) {
         boolean inventoryScreen = client.gui.screen() instanceof InventoryScreen;
         if ((client.gui.screen() == null || inventoryScreen) && (!this.onlyInInventory.get() || inventoryScreen)) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else {
               for (int i = 0; i < SLOTS.length; i++) {
                  EquipmentSlot slot = SLOTS[i];
                  ItemStack worn = player.getItemBySlot(slot);
                  if (!this.keepElytra.get() || slot != EquipmentSlot.CHEST || !worn.is(Items.ELYTRA)) {
                     double bestScore = score(worn, slot);
                     int bestIndex = -1;

                     for (int index = 0; index < 36; index++) {
                        double value = score(player.getInventory().getItem(index), slot);
                        if (value > bestScore) {
                           bestScore = value;
                           bestIndex = index;
                        }
                     }

                     if (bestIndex >= 0) {
                        int container = player.inventoryMenu.containerId;
                        int from = Inventories.menuSlot(bestIndex);
                        int armorSlot = 5 + i;
                        if (worn.isEmpty()) {
                           client.gameMode.handleContainerInput(container, from, 0, ContainerInput.QUICK_MOVE, player);
                        } else {
                           client.gameMode.handleContainerInput(container, from, 0, ContainerInput.PICKUP, player);
                           client.gameMode.handleContainerInput(container, armorSlot, 0, ContainerInput.PICKUP, player);
                           if (!player.inventoryMenu.getCarried().isEmpty()) {
                              client.gameMode.handleContainerInput(container, from, 0, ContainerInput.PICKUP, player);
                           }
                        }

                        this.cooldown = this.delay.intValue();
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private static double score(ItemStack stack, EquipmentSlot slot) {
      if (stack.isEmpty()) {
         return -1.0;
      } else {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         if (equippable != null && equippable.slot() == slot) {
            double armor = 0.0;
            double toughness = 0.0;

            for (Entry entry : ((ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)).modifiers()) {
               if (entry.attribute().is(Attributes.ARMOR)) {
                  armor += entry.modifier().amount();
               } else if (entry.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                  toughness += entry.modifier().amount();
               }
            }

            double durability = stack.isDamageableItem() ? 1.0 - (double)stack.getDamageValue() / Math.max(1, stack.getMaxDamage()) : 1.0;
            return armor + toughness * 0.5 + durability * 0.1;
         } else {
            return -1.0;
         }
      }
   }
}
