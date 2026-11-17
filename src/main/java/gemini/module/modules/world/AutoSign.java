package gemini.module.modules.world;

import net.minecraft.world.level.block.entity.SignTextSlot;
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
      new ListSetting("Lines", "Sign text, one entry per line (the first four are used, each cut to 15 characters)", ListSetting.Kind.TEXT, "Gemini", "was here")
   );
   private final BooleanSetting backToo = this.addSetting(
      new BooleanSetting("Back Side", "Also write the text when you edit the back of a sign (only one side can be written per edit)", false)
   );
   /** The packet this module is sending in place of the editor's, so the send hook does not replace it again. */
   private ServerboundSignUpdatePacket replacement;
   private final BooleanSetting closeScreen = this.addSetting(new BooleanSetting("Skip Editor", "Close the sign editor straight away", true));

   public AutoSign() {
      super("Auto Sign", "Fills in every sign you place with preset text.", Category.WORLD);
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (packet instanceof ServerboundSignUpdatePacket sign) {
         if (sign.slot() != SignTextSlot.FRONT && !this.backToo.get()) {
            return false;
         } else {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null || packet == this.replacement) {
               // Our own replacement passes through this hook again on its way out: let it go.
               return false;
            } else {
               this.replacement = new ServerboundSignUpdatePacket(sign.pos(), List.of(this.line(0), this.line(1), this.line(2), this.line(3)), sign.slot());
               try {
                  client.getConnection().send(this.replacement);
               } finally {
                  this.replacement = null;
               }

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
