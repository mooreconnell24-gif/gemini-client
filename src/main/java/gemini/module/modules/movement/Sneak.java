package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public final class Sneak extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Vanilla = really crouch, Packet = only the server", "Vanilla", "Vanilla", "Packet")
   );
   private final BooleanSetting onlyMoving = this.addSetting(
      new BooleanSetting("Only Moving", "Sneak only while you are walking", false).showIf(() -> this.mode.is("Vanilla"))
   );

   public Sneak() {
      super("Sneak", "Stay crouched without holding the key.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onEnable() {
      if (this.mode.is("Packet")) {
         sendInput(new Input(false, false, false, false, false, true, false));
      }
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         client.options.keyShift.setDown(MoveUtil.physicalKeyDown(client.options.keyShift));
         if (this.mode.is("Packet")) {
            sendInput(client.player.input == null ? Input.EMPTY : client.player.input.keyPresses);
         }
      }
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (this.mode.is("Packet") && packet instanceof ServerboundPlayerInputPacket input && !input.input().shift()) {
         Input base = input.input();
         sendInput(new Input(base.forward(), base.backward(), base.left(), base.right(), base.jump(), true, base.sprint()));
         return true;
      } else {
         return false;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && this.mode.is("Vanilla") && client.gui.screen() == null) {
         boolean sneak = !this.onlyMoving.get() || MoveUtil.hasInput(client.player);
         client.options.keyShift.setDown(sneak || MoveUtil.physicalKeyDown(client.options.keyShift));
      }
   }

   private static void sendInput(Input input) {
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      if (connection != null) {
         connection.send(new ServerboundPlayerInputPacket(input));
      }
   }
}
