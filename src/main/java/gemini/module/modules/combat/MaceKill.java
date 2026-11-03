package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

public final class MaceKill extends Module {
   private final NumberSetting height = this.addSetting(new NumberSetting("Spoof Height", "Simulated fall height in blocks", 6.0, 2.0, 22.0, 1.0));

   public MaceKill() {
      super("MaceKill", "Spoof a vertical drop packet for Mace smash damage.", Category.COMBAT);
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientPacketListener conn = client.getConnection();
      if (player != null && conn != null && player.getMainHandItem().is(Items.MACE)) {
         double h = this.height.get();
         conn.send(new Pos(player.getX(), player.getY() + h, player.getZ(), false, false));
         conn.send(new Pos(player.getX(), player.getY(), player.getZ(), false, false));
      }
   }
}
