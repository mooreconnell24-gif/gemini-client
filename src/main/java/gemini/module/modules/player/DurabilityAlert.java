package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Warns once when worn armour or a held tool gets close to breaking. */
public final class DurabilityAlert extends Module {
   private static final int NONE = PlayerMath.ALERT_NONE;
   private static final int CRITICAL = PlayerMath.ALERT_CRITICAL;
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private final NumberSetting percent = this.addSetting(new NumberSetting("Warn Below %", "Warn when durability drops below this (percent)", 10.0, 1.0, 50.0, 1.0));
   private final BooleanSetting chat = this.addSetting(new BooleanSetting("Chat", "Also write the warning in chat", true));
   private final NumberSetting critical = this.addSetting(
      new NumberSetting("Critical Uses", "Show a second, red warning when this many uses or fewer are left", 5.0, 1.0, 50.0, 1.0)
   );
   /** Per slot: the item last warned about (null when nothing is), and whether its red alert went out too. */
   private final Item[] warned = new Item[SLOTS.length];
   private final boolean[] criticalSent = new boolean[SLOTS.length];

   public DurabilityAlert() {
      super("Durability Alert", "Warns you before your armour or tools break.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      Arrays.fill(this.warned, null);
      Arrays.fill(this.criticalSent, false);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      for (int i = 0; i < SLOTS.length; i++) {
         ItemStack stack = player.getItemBySlot(SLOTS[i]);
         int level = stack.isEmpty() || !stack.isDamageableItem()
            ? NONE
            : PlayerMath.durabilityAlert(stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage(), this.percent.get(), this.critical.intValue());
         if (level == NONE) {
            // Repaired, swapped for something healthy, or taken off: warn again next time it gets low.
            this.warned[i] = null;
            this.criticalSent[i] = false;
            continue;
         }

         if (this.warned[i] != stack.getItem()) {
            this.warned[i] = stack.getItem();
            this.criticalSent[i] = level == CRITICAL;
            this.alert(stack, level == CRITICAL);
         } else if (level == CRITICAL && !this.criticalSent[i]) {
            // Already warned once; it is now about to break, which deserves a louder second alert.
            this.criticalSent[i] = true;
            this.alert(stack, true);
         }
      }
   }

   private void alert(ItemStack stack, boolean critical) {
      int left = stack.getMaxDamage() - stack.getDamageValue();
      double pct = 100.0 * left / Math.max(1, stack.getMaxDamage());
      String name = stack.getHoverName().getString();
      HudOverlay.toast(
         critical ? "About to break" : "Durability",
         name + " has " + left + (left == 1 ? " use" : " uses") + " left",
         critical ? HudOverlay.Severity.ERROR : HudOverlay.Severity.WARNING
      );
      if (this.chat.get()) {
         CommandManager.feedback(String.format(Locale.ROOT, "§bDurability §e%s §7is at %s%.0f%%§7 (%d uses left).", name, critical ? "§4" : "§c", pct, left));
      }
   }
}
