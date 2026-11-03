package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Warns once when worn armour or a held tool gets close to breaking. */
public final class DurabilityAlert extends Module {
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final NumberSetting percent = this.addSetting(new NumberSetting("Warn Below %", "Warn when durability drops below this", 10.0, 1.0, 50.0, 1.0));
   private final BooleanSetting chat = this.addSetting(new BooleanSetting("Chat", "Also write the warning in chat", true));
   private final Set<String> warned = new HashSet<>();

   public DurabilityAlert() {
      super("Durability Alert", "Warn you before your armour or tools break.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.warned.clear();
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      for (int i = 0; i < SLOTS.length; i++) {
         ItemStack stack = player.getItemBySlot(SLOTS[i]);
         String key = "slot" + i;
         if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            this.warned.removeIf(entry -> entry.startsWith(key + ":"));
            continue;
         }

         int left = stack.getMaxDamage() - stack.getDamageValue();
         double pct = 100.0 * left / stack.getMaxDamage();
         String id = key + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (pct < this.percent.get()) {
            if (!this.warned.contains(id)) {
               this.warned.removeIf(entry -> entry.startsWith(key + ":"));
               this.warned.add(id);
               String name = stack.getHoverName().getString();
               HudOverlay.pushNotification("Durability", name + " has " + left + " uses left", false);
               if (this.chat.get()) {
                  CommandManager.feedback(String.format(Locale.ROOT, "§bDurability §e%s §7is at §c%.0f%%§7 (%d uses left).", name, pct, left));
               }
            }
         } else {
            this.warned.remove(id);
         }
      }
   }
}
