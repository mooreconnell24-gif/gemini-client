package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class NoPush extends Module {
   private final BooleanSetting entities = this.addSetting(new BooleanSetting("Entities", "Cancel sideways pushes from entities around you", true));
   private final BooleanSetting water = this.addSetting(new BooleanSetting("Water Current", "Stop flowing water carrying you along", true));

   public NoPush() {
      super("NoPush", "Stops crowds of entities and water currents pushing you while you stand still.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client)) {
         LocalPlayer player = client.player;
         if (!MoveUtil.hasInput(player) && player.hurtTime == 0) {
            if (this.water.get() && player.isInWater()) {
               Vec3 v = player.getDeltaMovement();
               player.setDeltaMovement(v.x * 0.15, v.y, v.z * 0.15);
            } else if (this.entities.get() && player.onGround()) {
               Vec3 v = player.getDeltaMovement();
               if (v.horizontalDistanceSqr() > 1.0E-4 && v.horizontalDistanceSqr() < 0.04) {
                  player.setDeltaMovement(0.0, v.y, 0.0);
               }
            }
         }
      }
   }
}
