package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class WallClimb extends Module {
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Motion climbs at a steady speed; Pulse jumps every fourth tick; LedgeStep climbs in steps", "Motion", "Motion", "Pulse", "LedgeStep"));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Climb Speed", "Upward speed in Motion mode, in blocks per tick", 0.26, 0.1, 0.5, 0.02));
   private int tick;

   public WallClimb() {
      super("WallClimb", "Lets you climb walls by moving into them.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.player.horizontalCollision && MoveUtil.hasInput(client.player)) {
         LocalPlayer player = client.player;
         this.tick++;
         Vec3 m = player.getDeltaMovement();
         if (this.mode.is("Pulse")) {
            if (this.tick % 4 == 0) {
               player.setDeltaMovement(m.x, 0.42, m.z);
            }
         } else if (this.mode.is("LedgeStep")) {
            player.setDeltaMovement(m.x, this.tick % 3 == 0 ? 0.36 : 0.12, m.z);
         } else {
            player.setDeltaMovement(m.x, this.speed.get(), m.z);
         }
      }
   }
}
