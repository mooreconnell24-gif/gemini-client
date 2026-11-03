package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FastUse extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between item uses", 1.0, 1.0, 4.0, 1.0));
   private int wait;

   public FastUse() {
      super("FastUse", "Use throwables and XP bottles rapidly on right-click.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client) && client.gui.screen() == null && client.options.keyUse.isDown() && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         ItemStack main = player.getMainHandItem();
         if (main.is(Items.EXPERIENCE_BOTTLE)
            || main.is(Items.SNOWBALL)
            || main.is(Items.EGG)
            || main.is(Items.WIND_CHARGE)
            || main.is(Items.ENDER_PEARL)
            || main.is(Items.SPLASH_POTION)
            || main.is(Items.LINGERING_POTION)) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
         }
      }
   }
}
