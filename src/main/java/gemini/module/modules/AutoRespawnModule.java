package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;

public final class AutoRespawnModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks to wait on the death screen", 5.0, 0.0, 60.0, 1.0));
   private int waited;

   public AutoRespawnModule() {
      super("AutoRespawn", "Skip the death screen automatically.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (!(client.gui.screen() instanceof DeathScreen) && !player.isDeadOrDying()) {
            this.waited = 0;
         } else if (++this.waited > this.delay.intValue()) {
            player.respawn();
            client.gui.setScreen(null);
            this.waited = 0;
         }
      }
   }
}
