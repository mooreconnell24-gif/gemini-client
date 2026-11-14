package gemini.module.modules.combat;

import gemini.util.Inventories;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Damage;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoPearl extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Panic Health", "Throw pearl below this health", 6.0, 1.0, 16.0, 1.0));
   private int cooldown;

   public AutoPearl() {
      super("AutoPearl", "Throw an Ender Pearl when health drops critically low.", Category.COMBAT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.cooldown > 0) {
         this.cooldown--;
      } else if (ClientState.ready(client) && client.gui.screen() == null && Damage.health(client.player) <= this.health.floatValue()) {
         LocalPlayer player = client.player;
         if (Inventories.withHotbarItem(player, s -> s.is(Items.ENDER_PEARL), () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND))) {
            this.cooldown = 40;
         }
      }
   }
}
