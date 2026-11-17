package gemini.module.modules.movement;

import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.LinearFlightModuleBase;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.MoveUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

public final class FlightModule extends LinearFlightModuleBase {
   /** Blocks per tick of vanilla creative flight, so "Speed 1" feels the same in Creative and Motion. */
   private static final double CREATIVE_SPEED = 0.545;
   private final ModeSetting mode = this.addSetting(
      new ModeSetting(
         "Mode",
         "Creative flies exactly like creative mode; Motion stops dead when you let go of the keys; Glide sinks slowly; Jetpack pushes you up while Jump is held",
         "Creative",
         "Creative",
         "Motion",
         "Glide",
         "Jetpack"
      )
   );
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Speed", "Horizontal speed, where 1 is creative-mode flight", 1.0, 0.2, 10.0, 0.1)
   );
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical", "Climb and descent speed with Jump and Sneak, in blocks per tick", 0.45, 0.1, 3.0, 0.05).showIf(() -> !this.mode.is("Creative"))
   );
   private final NumberSetting fallSpeed = this.addSetting(
      new NumberSetting("Fall Speed", "How fast you sink while gliding, in blocks per tick", 0.1, 0.0, 0.5, 0.01).showIf(() -> this.mode.is("Glide"))
   );
   private final BooleanSetting noFall = this.addSetting(
      new BooleanSetting("No Fall", "Reset fall distance while flying so you take no fall damage", true).showIf(() -> !this.mode.is("Creative"))
   );
   private boolean previousFlying;
   /** True while the creative-style abilities written by this module are in place and have to be taken back. */
   private boolean abilitiesWritten;

   public FlightModule() {
      super("Flight", "Lets you fly in survival.", Category.MOVEMENT, Keys.fromGlfw('R'));
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Fly");
      names.add("Glide");
      return names;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      this.abilitiesWritten = false;
      this.previousFlying = player != null && player.getAbilities().flying;
   }

   @Override
   public void onDisable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.restoreAbilities(player, this.previousFlying);
      }

      this.abilitiesWritten = false;
   }

   /**
    * Hands flight back to the game mode. Whether the player may fly is taken from the game mode as it is now, not
    * from what it was when the module was switched on, so a switch from creative to survival while flying does
    * not leave survival with creative flight.
    */
   private void restoreAbilities(LocalPlayer player, boolean keepFlying) {
      boolean mayFly = player.isCreative() || player.isSpectator();
      boolean flying = player.isSpectator() || keepFlying && mayFly;
      write(player.getAbilities(), false, mayFly, flying, 1.0F);
      player.onUpdateAbilities();
      AttributeBoost.onServer(serverPlayer -> {
         write(serverPlayer.getAbilities(), false, mayFly, flying, 1.0F);
         serverPlayer.onUpdateAbilities();
      });
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      if (this.mode.is("Creative")) {
         float flySpeed = this.speed.floatValue();
         this.abilitiesWritten = true;
         write(player.getAbilities(), true, true, true, flySpeed);
         player.onUpdateAbilities();
         AttributeBoost.onServer(serverPlayer -> {
            write(serverPlayer.getAbilities(), true, true, true, flySpeed);
            serverPlayer.onUpdateAbilities();
         });
         return;
      }

      if (this.abilitiesWritten) {
         // The mode was changed away from creative-style flight while the module stayed on.
         this.abilitiesWritten = false;
         this.restoreAbilities(player, false);
      }

      Abilities abilities = player.getAbilities();
      if (abilities.flying && !player.isCreative() && !player.isSpectator()) {
         abilities.flying = false;
      }

      if (player.isFallFlying() || player.isPassenger()) {
         return;
      }

      boolean acted;
      if (this.mode.is("Jetpack")) {
         acted = this.jetpack(player);
      } else if (this.mode.is("Glide")) {
         acted = this.glide(player);
      } else {
         this.motion(player);
         acted = true;
      }

      if (acted && this.noFall.get()) {
         AttributeBoost.resetFall(player);
      }
   }

   /** Movement keys map straight to a velocity; with no key held the player hangs still. */
   private void motion(LocalPlayer player) {
      double y = verticalInput(player) * this.vertical.get();
      if (MoveUtil.hasInput(player)) {
         player.setDeltaMovement(player.getDeltaMovement().x, y, player.getDeltaMovement().z);
         MoveUtil.setHorizontal(player, CREATIVE_SPEED * this.speed.get());
      } else {
         player.setDeltaMovement(0.0, y, 0.0);
      }
   }

   private boolean jetpack(LocalPlayer player) {
      if (!jumpHeld(player)) {
         return false;
      }

      Vec3 motion = player.getDeltaMovement();
      player.setDeltaMovement(motion.x, Math.min(this.vertical.get(), motion.y + 0.12), motion.z);
      if (MoveUtil.hasInput(player)) {
         MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * Math.max(1.0, this.speed.get()));
      }

      return true;
   }

   /** Only acts in the air, so walking and jumping on the ground stay vanilla. */
   private boolean glide(LocalPlayer player) {
      if (!MovementHelpers.airborne(player)) {
         return false;
      }

      Vec3 motion = player.getDeltaMovement();
      double y = motion.y;
      if (sneakHeld(player)) {
         y = -Math.max(0.2, this.vertical.get());
      } else if (motion.y < -this.fallSpeed.get()) {
         y = -this.fallSpeed.get();
      }

      player.setDeltaMovement(motion.x, y, motion.z);
      if (this.speed.get() > 1.0 && MoveUtil.hasInput(player)) {
         MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * this.speed.get());
      }

      return true;
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
