package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AutoWalkModule extends Module {
   private final BooleanSetting sprint = this.addSetting(new BooleanSetting("Sprint", "Sprint while walking", true));
   private final BooleanSetting jump = this.addSetting(new BooleanSetting("Auto Jump", "Hop over obstacles in the way", true));

   public AutoWalkModule() {
      super("AutoWalk", "Walk forward automatically.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (client.options != null) {
         // Hand control back to the real keys so holding W while switching AutoWalk off keeps walking.
         client.options.keyUp.setDown(MoveUtil.physicalKeyDown(client.options.keyUp));
         if (this.sprint.get()) {
            client.options.keySprint.setDown(MoveUtil.physicalKeyDown(client.options.keySprint));
         }
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gui.screen() == null) {
         client.options.keyUp.setDown(true);
         if (this.sprint.get()) {
            client.options.keySprint.setDown(true);
         }

         if (this.jump.get() && player.horizontalCollision && player.onGround()) {
            player.jumpFromGround();
         }
      }
   }
}
