package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;

public final class AutoTunnel extends Module {
   private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Blocks to tunnel ahead", 64.0, 4.0, 512.0, 4.0));

   public AutoTunnel() {
      super("Auto Tunnel", "Dig a 1x2 tunnel and bridge gaps automatically.", Category.WORLD);
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
      Direction dir = player == null ? Direction.NORTH : player.getDirection();
      BaritoneBridge.startTunnel(dir, this.length.intValue());
      BaritoneBridge.claimTask(this);
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

   @Override
   public String info() {
      return this.length.intValue() + "m";
   }
}
