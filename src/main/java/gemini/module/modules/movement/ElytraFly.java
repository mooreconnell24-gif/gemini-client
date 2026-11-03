package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class ElytraFly extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Control = fly where you look at a fixed speed", "Control", "Control", "Boost")
   );
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Flight speed in blocks per tick", 1.2, 0.2, 5.0, 0.1));
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical Speed", "Climb and descent speed with Jump and Sneak", 0.5, 0.1, 2.0, 0.1).showIf(() -> this.mode.is("Control"))
   );
   private final BooleanSetting hover = this.addSetting(
      new BooleanSetting("Hover", "Hang still in the air when no movement key is held", true).showIf(() -> this.mode.is("Control"))
   );
   private final BooleanSetting takeoff = this.addSetting(new BooleanSetting("Auto Takeoff", "Open the elytra when you press Jump in mid-air", true));

   public ElytraFly() {
      super("Elytra Fly", "Fly with an elytra with full control.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (MovementHelpers.ready(client) && player.input != null) {
         Input keys = player.input.keyPresses;
         if (!player.isFallFlying()) {
            ClientPacketListener connection = client.getConnection();
            if (this.takeoff.get()
               && connection != null
               && keys.jump()
               && !player.onGround()
               && !player.isInWater()
               && !player.isPassenger()
               && player.getDeltaMovement().y < 0.0
               && player.tryToStartFallFlying()) {
               connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
            }
         } else {
            Vec3 look = player.getLookAngle();
            Vec3 motion = player.getDeltaMovement();
            if (this.mode.is("Boost")) {
               if (keys.forward()) {
                  Vec3 boosted = motion.add(look.scale(0.1));
                  double length = boosted.length();
                  player.setDeltaMovement(length > this.speed.get() ? boosted.scale(this.speed.get() / length) : boosted);
               }
            } else {
               Vec3 target;
               if (keys.forward()) {
                  target = look.scale(this.speed.get());
               } else if (keys.backward()) {
                  target = look.scale(-this.speed.get());
               } else if (this.hover.get()) {
                  target = Vec3.ZERO;
               } else {
                  target = motion;
               }

               double y = target.y;
               if (keys.jump()) {
                  y += this.vertical.get();
               }

               if (keys.shift()) {
                  y -= this.vertical.get();
               }

               player.setDeltaMovement(target.x, y, target.z);
            }
         }
      }
   }
}
