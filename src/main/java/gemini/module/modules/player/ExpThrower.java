package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.util.RotationManager;
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
   private static final EquipmentSlot[] GEAR = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
   };
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bottles", 4.0, 1.0, 20.0, 1.0));
   private final BooleanSetting onlyDamaged = this.addSetting(
      new BooleanSetting("Only When Damaged", "Stop once nothing you wear or hold with Mending needs repairing", true)
   );
   private final BooleanSetting lookDown = this.addSetting(
      new BooleanSetting("Look Down", "Aim the bottles at your feet (server-side only, the camera does not move)", true)
   );
   private int cooldown;

   public ExpThrower() {
      super("EXP Thrower", "Throw experience bottles to mend your gear.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && client.gui.screen() == null && (!this.onlyDamaged.get() || needsRepair(player))) {
         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }

         boolean aimed = this.lookDown.get();
         // A more important module holding the rotation this tick wins; throwing anyway would send the XP elsewhere.
         if (aimed && !RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, true, this.priority(), 2.0F)) {
            return;
         }

         if (PlayerHelpers.useHotbarItem(client, player, Items.EXPERIENCE_BOTTLE, aimed)) {
            this.cooldown = this.delay.intValue();
         }
      }
   }

   /** Experience only repairs Mending items, so only those count. */
   private static boolean needsRepair(LocalPlayer player) {
      for (EquipmentSlot slot : GEAR) {
         ItemStack stack = player.getItemBySlot(slot);
         if (stack.isDamaged() && PlayerHelpers.mending(stack)) {
            return true;
         }
      }

      return false;
   }
}
