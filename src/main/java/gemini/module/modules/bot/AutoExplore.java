package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AutoExplore extends Module {
   public AutoExplore() {
      super("Auto Explore", "Autonomously spiral outward to explore chunks.", Category.WORLD);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         BaritoneBridge.startExplore(player.blockPosition());
         BaritoneBridge.claimTask(this);
      }
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopTaskOf(this);
   }

   /** Switches the module off once its task is over or another request replaced it, so the list shows what is really running. */
   @Override
   public void onTick() {
      if (!BaritoneBridge.holdsTask(this)) {
         this.setEnabled(false);
      }
   }
}
