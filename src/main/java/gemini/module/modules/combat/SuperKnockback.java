package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class SuperKnockback extends Module {
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "How the sprint is restarted on the hit", "Packet", "Packet", "WTap", "StopStart"));
   private final BooleanSetting onlyMoving = this.addSetting(new BooleanSetting("Only Moving", "Only while you are pressing a movement key", true));

   public SuperKnockback() {
      super("SuperKnockback", "Restarts your sprint on each hit so the hit deals sprint knockback.", Category.COMBAT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientPacketListener conn = client.getConnection();
      if (player != null && conn != null && target instanceof LivingEntity && (!this.onlyMoving.get() || MoveUtil.hasInput(player))) {
         if (this.mode.is("WTap")) {
            player.setSprinting(false);
            conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
            player.setSprinting(true);
         } else if (this.mode.is("StopStart")) {
            if (player.isSprinting()) {
               conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
            }
            conn.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
            player.setSprinting(true);
         } else {
            conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
            conn.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
            player.setSprinting(true);
         }
      }
   }
}
