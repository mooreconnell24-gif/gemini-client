package gemini.module.modules.combat;

import java.util.List;
import java.util.ArrayList;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class SuperKnockback extends Module {
   private final BooleanSetting onlyMoving = this.addSetting(new BooleanSetting("Only Moving", "Only while you are pressing a movement key", true));

   public SuperKnockback() {
      super("Super Knockback", "Restarts your sprint on each hit so every hit deals sprint knockback.", Category.COMBAT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("WTap");
      return names;
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientPacketListener conn = client.getConnection();
      if (player != null && conn != null && target instanceof LivingEntity && (!this.onlyMoving.get() || MoveUtil.hasInput(player))) {
         // Telling the game the sprint stopped and started again makes this hit count as a fresh sprint hit.
         conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
         conn.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
         player.setSprinting(true);
      }
   }
}
