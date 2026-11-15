package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import gemini.util.AttackAssist;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class CriticalsModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Packet crits every full-strength hit without moving you; Hop and Mini jump on hit so your next swing lands while falling", "Packet", "Packet", "Hop", "Mini")
   );

   public CriticalsModule() {
      super("Criticals", "Force critical hits when attacking.", Category.COMBAT);
   }

   // "Packet" keeps its name for saved configs, but no longer sends packets: Minecraft 26.3 disconnects a client
   // that sends two position packets in a tick, which is exactly what the old trick did.
   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         AttackAssist.criticals(player.getUUID(), this.mode.is("Packet"));
      }
   }

   @Override
   public void onDisable() {
      AttackAssist.criticals(null, false);
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
               // Packet mode is applied by AttackAssist where the hit is calculated; nothing to send from here.
         }
      }
   }
}
