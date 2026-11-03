package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

public final class BoatFly extends Module {
   private final NumberSetting horizontal = this.addSetting(new NumberSetting("Horizontal Speed", "Speed in blocks per tick", 1.0, 0.1, 4.0, 0.1));
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical Speed", "Climb and descent speed with Jump and Sneak", 0.5, 0.1, 2.0, 0.1)
   );
   private AbstractBoat flying;

   public BoatFly() {
      super("Boat Fly", "Fly while sitting in a boat.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      if (this.flying != null) {
         this.flying.setNoGravity(false);
         this.flying = null;
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (MovementHelpers.ready(client) && player.input != null) {
         if (player.getVehicle() instanceof AbstractBoat boat) {
            if (this.flying != boat) {
               this.onDisable();
               this.flying = boat;
            }

            boat.setNoGravity(true);
            Input keys = player.input.keyPresses;
            double y = 0.0;
            if (keys.jump()) {
               y += this.vertical.get();
            }

            if (keys.shift()) {
               y -= this.vertical.get();
            }

            Vec3 direction = MoveUtil.direction(player);
            double x = direction == null ? 0.0 : direction.x * this.horizontal.get();
            double z = direction == null ? 0.0 : direction.z * this.horizontal.get();
            boat.setDeltaMovement(x, y, z);
         } else {
            this.onDisable();
         }
      }
   }
}
