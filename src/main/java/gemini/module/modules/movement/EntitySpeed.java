package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class EntitySpeed extends Module {
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
      if (MovementHelpers.ready(client)) {
         Entity vehicle = player.getVehicle();
         Vec3 direction = MoveUtil.direction(player);
         if (vehicle != null && direction != null && (!this.onlyGround.get() || vehicle.onGround())) {
            vehicle.setDeltaMovement(direction.x * this.speed.get(), vehicle.getDeltaMovement().y, direction.z * this.speed.get());
         }
      }
   }
}
