package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;

public final class AutoTunnel extends Module {
   private final NumberSetting length = this.addSetting(new NumberSetting("Length", "Blocks to tunnel ahead", 64.0, 4.0, 512.0, 4.0));

   public AutoTunnel() {
      super("Auto Tunnel", "Dig a 1x2 tunnel and bridge gaps automatically.", Category.WORLD);
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
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopAll();
   }

   @Override
   public String info() {
      return this.length.intValue() + "m";
   }
}
