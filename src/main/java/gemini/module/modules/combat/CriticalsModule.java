package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CriticalsModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Packet crits every hit; Hop and Mini jump on hit so your next swing lands while falling", "Packet", "Packet", "Hop", "Mini")
   );

   public CriticalsModule() {
      super("Criticals", "Force critical hits when attacking.", Category.COMBAT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onAttack(Entity target) {
      LocalPlayer player = Minecraft.getInstance().player;
      // Vanilla only crits fully charged hits, so a weak hit is not worth faking a fall for.
      if (player != null
         && player.onGround()
         && !player.isInWater()
         && !player.isInLava()
         && !player.onClimbable()
         && !player.isPassenger()
         && player.getAttackStrengthScale(0.5F) > 0.9F) {
         String motion = this.mode.get();
         switch (motion) {
            case "Hop":
               player.jumpFromGround();
               break;
            case "Mini":
               Vec3 motionx = player.getDeltaMovement();
               player.setDeltaMovement(motionx.x, 0.11, motionx.z);
               break;
            default:
               if (player.connection == null) {
                  return;
               }

               double x = player.getX();
               double y = player.getY();
               double z = player.getZ();
               player.connection.send(new Pos(x, y + 0.0625, z, false, player.horizontalCollision));
               player.connection.send(new Pos(x, y, z, false, player.horizontalCollision));
         }
      }
   }
}
