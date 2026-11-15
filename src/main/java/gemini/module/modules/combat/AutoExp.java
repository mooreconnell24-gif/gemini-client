package gemini.module.modules.combat;

import gemini.util.Inventories;
import gemini.util.ClientState;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public final class AutoExp extends Module {
   private final NumberSetting threshold = this.addSetting(
      new NumberSetting("Durability %", "Repair when armor durability is below %", 60.0, 5.0, 99.0, 1.0)
   );
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between throws", 2.0, 1.0, 20.0, 1.0));
   private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim down in packets only", true));
   private int wait;

   public AutoExp() {
      super("Auto EXP", "Throw XP bottles at your feet when armor is damaged.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (CombatHelpers.armorDamaged(player, this.threshold.intValue())) {
            boolean aimed = RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, this.silent.get(), ActionPriority.WORLD_UTILITY, 2.0F);
            if (aimed && this.wait-- <= 0) {
               this.wait = this.delay.intValue();
               Inventories.withHotbarItem(
                  player, stack -> stack.is(Items.EXPERIENCE_BOTTLE), () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND))
               );
            }
         }
      }
   }
}
