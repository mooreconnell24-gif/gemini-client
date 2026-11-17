package gemini.module.modules.player;

import java.util.List;
import java.util.ArrayList;
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
   private final NumberSetting durability = this.addSetting(
      new NumberSetting("Durability %", "Only throw while a Mending item is below this durability (percent); 100 repairs any damage", 100.0, 5.0, 100.0, 1.0)
         .showIf(this.onlyDamaged::get)
   );
   private final BooleanSetting lookDown = this.addSetting(
      new BooleanSetting("Look Down", "Aim the bottles at your feet without moving your camera", true)
   );
   private int cooldown;

   public ExpThrower() {
      super("Auto EXP", "Throws experience bottles at your feet to repair your Mending gear.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("EXP Thrower");
      names.add("Auto XP");
      return names;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && client.gui.screen() == null && (!this.onlyDamaged.get() || needsRepair(player, this.durability.get()))) {
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
   private static boolean needsRepair(LocalPlayer player, double belowPercent) {
      for (EquipmentSlot slot : GEAR) {
         ItemStack stack = player.getItemBySlot(slot);
         if (stack.isDamaged() && PlayerHelpers.mending(stack)) {
            double left = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (belowPercent >= 100.0 || left < belowPercent) {
               return true;
            }
         }
      }

      return false;
   }
}
