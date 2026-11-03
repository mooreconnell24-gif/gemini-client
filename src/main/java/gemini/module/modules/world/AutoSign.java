package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

public final class AutoSign extends Module {
   private final ListSetting lines = this.addSetting(
      new ListSetting("Lines", "Sign text, one entry per line (first four are used)", ListSetting.Kind.TEXT, "Gemini", "was here")
   );
   private final BooleanSetting backToo = this.addSetting(new BooleanSetting("Back Side", "Also write on the back of the sign", false));
   private final BooleanSetting closeScreen = this.addSetting(new BooleanSetting("Skip Editor", "Close the sign editor instantly", true));

   public AutoSign() {
      super("Auto Sign", "Fill in every sign you place with preset text.", Category.WORLD);
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (packet instanceof ServerboundSignUpdatePacket sign) {
         if (!sign.isFrontText() && !this.backToo.get()) {
            return false;
         } else {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) {
               return false;
            } else {
               client.getConnection()
                  .send(new ServerboundSignUpdatePacket(sign.getPos(), sign.isFrontText(), this.line(0), this.line(1), this.line(2), this.line(3)));
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.closeScreen.get() && client.gui.screen() instanceof AbstractSignEditScreen screen) {
         screen.onClose();
      }
   }

   private String line(int index) {
      List<String> all = this.lines.get();
      String value = index < all.size() ? all.get(index) : "";
      return value == null ? "" : (value.length() > 15 ? value.substring(0, 15) : value);
   }
}
