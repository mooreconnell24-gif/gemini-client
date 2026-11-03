package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ExpThrower extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bottles", 4.0, 1.0, 20.0, 1.0));
   private final BooleanSetting onlyDamaged = this.addSetting(new BooleanSetting("Only When Damaged", "Stop once no enchanted gear needs repairing", true));
   private final BooleanSetting lookDown = this.addSetting(new BooleanSetting("Look Down", "Face the ground so the XP lands at your feet", true));
   private int cooldown;

   public ExpThrower() {
      super("EXP Thrower", "Throw experience bottles to mend your gear.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (PlayerHelpers.ready(client) && client.gui.screen() == null && (!this.onlyDamaged.get() || needsRepair(player))) {
         if (this.lookDown.get() && Math.abs(player.getXRot() - 90.0F) > 0.5F) {
            player.setXRot(90.0F);
         } else if (this.cooldown > 0) {
            this.cooldown--;
         } else if (PlayerHelpers.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE)) {
            this.cooldown = this.delay.intValue();
         }
      }
   }

   private static boolean needsRepair(LocalPlayer player) {
      for (EquipmentSlot slot : new EquipmentSlot[]{
         EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
      }) {
         ItemStack stack = player.getItemBySlot(slot);
         if (stack.isDamaged() && stack.isEnchanted()) {
            return true;
         }
      }

      return false;
   }
}
