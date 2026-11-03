package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoTotemModule extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Only swap when at or below this HP (20 = always)", 20.0, 2.0, 20.0, 1.0));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between swaps", 2.0, 0.0, 20.0, 1.0));
   private final BooleanSetting replace = this.addSetting(new BooleanSetting("Replace Offhand", "Swap even if the offhand holds another item", true));
   private final BooleanSetting hotbarOnly = this.addSetting(new BooleanSetting("Hotbar Only", "Only pull totems from the hotbar", false));
   private int cooldown;

   public AutoTotemModule() {
      super("AutoTotem", "Keep a totem of undying in your offhand.", Category.COMBAT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   public boolean wantsOffhand(LocalPlayer player) {
      return player != null && Damage.health(player) <= this.health.floatValue();
   }

   @Override
   public String info() {
      return this.health.intValue() >= 20 ? "always" : this.health.intValue() + "hp";
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null && Inventories.inventoryOpenOrClosed(player)) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            ItemStack offhand = player.getOffhandItem();
            if (!offhand.is(Items.TOTEM_OF_UNDYING) && !(Damage.health(player) > this.health.floatValue()) && (this.replace.get() || offhand.isEmpty())) {
               int index = this.findTotem(player);
               if (index >= 0) {
                  this.cooldown = this.delay.intValue();
                  Inventories.swapToOffhand(client, player, index);
               }
            }
         }
      }
   }

   private int findTotem(LocalPlayer player) {
      return Inventories.find(player, stack -> stack.is(Items.TOTEM_OF_UNDYING), this.hotbarOnly.get());
   }
}
