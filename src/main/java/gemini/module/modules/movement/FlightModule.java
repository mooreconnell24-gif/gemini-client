package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

public final class FlightModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode", "Auto flies like creative mode in your single-player world", "Auto", "Auto", "Strict", "Packet", "Creative", "Velocity", "Glide", "Jetpack"
      )
   );
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Horizontal flight speed", 0.55, 0.2, 5.0, 0.1));
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical", "Vertical climb/descent speed", 0.45, 0.1, 3.0, 0.05).showIf(() -> !this.mode.is("Creative"))
   );
   private final BooleanSetting setbackPause = this.addSetting(new BooleanSetting("Setback Damp", "Smooth velocity on server position setback", true));
   private final BooleanSetting noFall = this.addSetting(new BooleanSetting("No Fall", "Reset fall distance while flying", true));
   private boolean previousMayFly;
   private boolean previousFlying;

   public FlightModule() {
      super("Flight", "Fly in survival with your choice of flight style.", Category.MOVEMENT, 71);
   }

   private boolean creativeMode() {
      return this.mode.is("Creative") || this.mode.is("Auto") && Minecraft.getInstance().hasSingleplayerServer();
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.previousMayFly = player.getAbilities().mayfly;
         this.previousFlying = player.getAbilities().flying;
      }
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         boolean mayFly = this.previousMayFly || player.isCreative() || player.isSpectator();
         boolean flying = this.previousFlying && mayFly;
         write(player.getAbilities(), false, mayFly, flying, 1.0F);
         player.onUpdateAbilities();
         AttributeBoost.onServer(serverPlayer -> {
            write(serverPlayer.getAbilities(), false, mayFly, flying, 1.0F);
            serverPlayer.onUpdateAbilities();
         });
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (this.setbackPause.get() && !this.creativeMode() && MoveUtil.hasRecentSetback()) {
            MoveUtil.smoothVelocity(player, 0.35);
            return;
         }

         if (this.creativeMode()) {
            float flySpeed = this.speed.floatValue();
            write(player.getAbilities(), true, true, true, flySpeed);
            player.onUpdateAbilities();
            AttributeBoost.onServer(serverPlayer -> {
               write(serverPlayer.getAbilities(), true, true, true, flySpeed);
               serverPlayer.onUpdateAbilities();
            });
         } else {
            Abilities abilities = player.getAbilities();
            if (abilities.flying && !player.isCreative() && !player.isSpectator()) {
               abilities.flying = false;
            }

            if (this.mode.is("Jetpack")) {
               this.jetpack(player);
            } else if (this.mode.is("Glide")) {
               this.glide(player);
            } else if (this.mode.is("Packet")) {
               this.packetFlight(client, player);
            } else if (this.mode.is("Strict")) {
               this.strictFlight(player);
            } else {
               this.velocityFlight(player);
            }
         }
      }
   }

   private void strictFlight(LocalPlayer player) {
      boolean jump = player.input != null && player.input.keyPresses.jump();
      boolean sneak = player.input != null && player.input.keyPresses.shift();
      double targetY = 0.0;
      if (jump) {
         targetY = Math.min(0.42, this.vertical.get());
      } else if (sneak) {
         targetY = -Math.min(0.42, this.vertical.get());
      }

      double maxHoriz = 0.2806 * Math.min(this.speed.get(), 1.65);
      Vec3 dir = MoveUtil.direction(player);
      Vec3 current = player.getDeltaMovement();
      double targetX = dir == null ? 0.0 : dir.x * maxHoriz;
      double targetZ = dir == null ? 0.0 : dir.z * maxHoriz;
      player.setDeltaMovement(
         MoveUtil.lerpMotion(current.x, targetX, 0.55), MoveUtil.lerpMotion(current.y, targetY, 0.65), MoveUtil.lerpMotion(current.z, targetZ, 0.55)
      );
      if (this.noFall.get()) {
         AttributeBoost.resetFall(player);
      }
   }

   private void packetFlight(Minecraft client, LocalPlayer player) {
      boolean jump = player.input != null && player.input.keyPresses.jump();
      boolean sneak = player.input != null && player.input.keyPresses.shift();
      double dy = jump ? 0.062 : (sneak ? -0.062 : 0.0);
      Vec3 dir = MoveUtil.direction(player);
      double step = 0.22 * Math.min(this.speed.get(), 1.5);
      double dx = dir == null ? 0.0 : dir.x * step;
      double dz = dir == null ? 0.0 : dir.z * step;
      player.setDeltaMovement(0.0, 0.0, 0.0);
      Vec3 next = player.position().add(dx, dy, dz);
      player.setPos(next);
      ClientPacketListener conn = client.getConnection();
      if (conn != null) {
         conn.send(new Pos(next.x, next.y, next.z, false, false));
      }

      if (this.noFall.get()) {
         AttributeBoost.resetFall(player);
      }
   }

   private void velocityFlight(LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      double y = 0.0;
      boolean jump = player.input != null && player.input.keyPresses.jump();
      boolean sneak = player.input != null && player.input.keyPresses.shift();
      if (jump) {
         y += this.vertical.get();
      }

      if (sneak) {
         y -= this.vertical.get();
      }

      player.setDeltaMovement(motion.x, y, motion.z);
      if (MoveUtil.hasInput(player)) {
         MoveUtil.setHorizontal(player, 0.2806 * this.speed.get() * 1.5);
      } else {
         player.setDeltaMovement(0.0, y, 0.0);
      }

      if (this.noFall.get()) {
         AttributeBoost.resetFall(player);
      }
   }

   private void jetpack(LocalPlayer player) {
      boolean jump = player.input != null && player.input.keyPresses.jump();
      if (jump) {
         Vec3 motion = player.getDeltaMovement();
         double y = Math.min(this.vertical.get(), motion.y + 0.12);
         player.setDeltaMovement(motion.x, y, motion.z);
         if (MoveUtil.hasInput(player)) {
            MoveUtil.setHorizontal(player, 0.2806 * this.speed.get());
         }

         if (this.noFall.get()) {
            AttributeBoost.resetFall(player);
         }
      }
   }

   private void glide(LocalPlayer player) {
      boolean jump = player.input != null && player.input.keyPresses.jump();
      boolean sneak = player.input != null && player.input.keyPresses.shift();
      double y;
      if (jump) {
         y = this.vertical.get() * 0.35;
      } else if (sneak) {
         y = -Math.max(0.2, this.vertical.get());
      } else {
         y = -0.03;
      }

      player.setDeltaMovement(player.getDeltaMovement().x, y, player.getDeltaMovement().z);
      if (MoveUtil.hasInput(player)) {
         MoveUtil.setHorizontal(player, 0.2806 * this.speed.get());
      } else {
         player.setDeltaMovement(0.0, y, 0.0);
      }

      if (this.noFall.get()) {
         AttributeBoost.resetFall(player);
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   private static void write(Abilities abilities, boolean active, boolean mayFly, boolean flying, float speed) {
      if (active) {
         abilities.mayfly = true;
         abilities.flying = true;
         abilities.setFlyingSpeed(0.05F * speed);
      } else {
         abilities.mayfly = mayFly;
         abilities.flying = flying && mayFly;
         abilities.setFlyingSpeed(0.05F);
      }
   }
}
