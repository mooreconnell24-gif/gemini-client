package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;

public final class BowSpam extends Module {
   private final NumberSetting charge = this.addSetting(
      new NumberSetting("Charge", "Ticks to draw before firing (20 = full draw)", 5.0, 3.0, 20.0, 1.0)
   );

   public BowSpam() {
      super("Bow Spam", "Rapidly release and redraw your bow.", Category.COMBAT);
   }

   @Override
   public String info() {
      return this.charge.intValue() + "t";
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (CombatHelpers.ready(client)
         && client.gui.screen() == null
         && player.isUsingItem()
         && player.getUseItem().getItem() instanceof BowItem
         && player.getTicksUsingItem() >= this.charge.intValue()) {
         InteractionHand hand = player.getUsedItemHand();
         client.gameMode.releaseUsingItem(player);
         client.gameMode.useItem(player, hand);
      }
   }
}
