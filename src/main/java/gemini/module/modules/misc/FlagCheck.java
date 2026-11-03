package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.ui.HudOverlay;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

public final class FlagCheck extends Module {
   private int flags;

   public FlagCheck() {
      super("FlagCheck", "Count and alert when the world snaps your position back.", Category.CLIENT);
   }

   @Override
   public void onEnable() {
      this.flags = 0;
   }

   @Override
   public String info() {
      return Integer.toString(this.flags);
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerPositionPacket) {
         this.flags++;
         HudOverlay.pushNotification("FlagCheck", "Setback #" + this.flags + " detected", false);
      }

      return false;
   }
}
