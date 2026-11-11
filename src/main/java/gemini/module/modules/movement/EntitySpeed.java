package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.LinearFlightModuleBase;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class EntitySpeed extends LinearFlightModuleBase {
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Mount speed in blocks per tick", 0.6, 0.1, 3.0, 0.05));
   private final BooleanSetting onlyGround = this.addSetting(new BooleanSetting("Only On Ground", "Leave airborne mounts alone", false));

   public EntitySpeed() {
      super("Entity Speed", "Speed up the vehicle or animal you are riding.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return String.format("%.2f", this.speed.get());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (ClientState.ready(client)) {
         Entity vehicle = player.getVehicle();
         // Only while a direction is held: a mount standing still keeps its own motion.
         if (vehicle != null && MoveUtil.direction(player) != null && (!this.onlyGround.get() || vehicle.onGround())) {
            Vec3 flat = horizontal(player, this.speed.get());
            vehicle.setDeltaMovement(flat.x, vehicle.getDeltaMovement().y, flat.z);
         }
      }
   }
}
