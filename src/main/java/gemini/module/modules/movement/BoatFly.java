package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.LinearFlightModuleBase;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

public final class BoatFly extends LinearFlightModuleBase {
   private final NumberSetting horizontal = this.addSetting(new NumberSetting("Horizontal Speed", "Speed in blocks per tick", 1.0, 0.1, 4.0, 0.1));
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical Speed", "Climb and descent speed with Jump and Sneak", 0.5, 0.1, 2.0, 0.1)
   );
   private AbstractBoat flying;

   public BoatFly() {
      super("Boat Fly", "Fly while sitting in a boat.", Category.MOVEMENT);
   }

   private void release() {
      if (this.flying != null) {
         this.flying.setNoGravity(false);
         this.flying = null;
      }
   }

   @Override
   public void onDisable() {
      this.release();
   }

   /** The boat belongs to the world that was left; it must not be kept or touched from the new one. */
   @Override
   public void onWorldChange() {
      this.flying = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client) && player.input != null) {
         if (player.getVehicle() instanceof AbstractBoat boat) {
            if (this.flying != boat) {
               this.release();
               this.flying = boat;
            }

            boat.setNoGravity(true);
            boat.setDeltaMovement(steering(player, this.horizontal.get(), this.vertical.get()));
         } else {
            this.release();
         }
      }
   }
}
