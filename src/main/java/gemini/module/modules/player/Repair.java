package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps what you hold and wear repaired. While on, anything that drops below the threshold is restored to full
 * durability; "Repair Now" does it once regardless of the threshold.
 */
public final class Repair extends Module {
   private static final EquipmentSlot[] SLOTS = {
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final String[] SLOT_NAMES = {"weapon.mainhand", "weapon.offhand", "armor.head", "armor.chest", "armor.legs", "armor.feet"};
   private static final int CHECK_TICKS = 10;
   private final NumberSetting below = this.addSetting(new NumberSetting("Below", "Repair an item once its durability is under this percent", 50.0, 1.0, 100.0, 1.0));
   private final BooleanSetting armour = this.addSetting(new BooleanSetting("Armour", "Also repair what you are wearing", true));
   private int wait;

   public Repair() {
      super("Repair", "Keep held items and armour from wearing out.", Category.PLAYER);
      this.addSetting(new ActionSetting("Repair Now", "Fully repair what you hold and wear right away", () -> this.repair(100.0, true)));
   }

   @Override
   public void onEnable() {
      this.wait = 0;
   }

   @Override
   public void onTick() {
      if (this.wait-- <= 0) {
         this.wait = CHECK_TICKS;
         this.repair(this.below.get(), false);
      }
   }

   private void repair(double belowPercent, boolean report) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client)) {
         return;
      }

      if (!SingleplayerGate.allowed()) {
         if (report) {
            CommandManager.feedback(SingleplayerGate.denialMessage());
         }

         return;
      }

      int repaired = 0;
      for (int i = 0; i < SLOTS.length; i++) {
         if (i >= 2 && !this.armour.get()) {
            break;
         }

         ItemStack stack = player.getItemBySlot(SLOTS[i]);
         if (!stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged()) {
            double left = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (left < belowPercent
               && SpCommands.run("item modify entity @s " + SLOT_NAMES[i] + " {type:\"minecraft:set_damage\",damage:1.0f,add:false}")) {
               repaired++;
            }
         }
      }

      if (report) {
         CommandManager.feedback(repaired == 0 ? "§bRepair §7nothing you hold or wear is damaged." : "§bRepair §7repaired §f" + repaired + " §7item" + (repaired == 1 ? "" : "s") + ".");
      }
   }
}
