package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import gemini.util.SpCommands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CriticalsModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Packet makes every hit a critical; Hop and Mini jump on the hit so your next swing lands while falling", "Packet", "Packet", "Hop", "Mini")
   );

   public CriticalsModule() {
      super("Criticals", "Makes your melee hits land as critical hits.", Category.COMBAT);
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
               // Puts the server's copy of you a moment into a fall; the task runs before the attack packet is
               // handled (tasks between ticks, packets at the start of the next). The old two extra position
               // packets got the player disconnected: the 26.x server allows one position per tick.
               SpCommands.onServer(server -> {
                  server.fallDistance = Math.max(server.fallDistance, 0.0625);
                  server.setOnGround(false);
               });
         }
      }
   }
}
