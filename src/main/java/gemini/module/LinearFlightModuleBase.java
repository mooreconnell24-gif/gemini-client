package gemini.module;

import gemini.util.MoveUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A module that turns the movement keys straight into a velocity: Flight, Boat Fly, Entity Speed. Each of them
 * read the keys and built the same vector by hand: the direction the player is steering times a horizontal speed,
 * plus jump and sneak as up and down.
 */
public abstract class LinearFlightModuleBase extends Module {
   protected LinearFlightModuleBase(String name, String description, Category category) {
      super(name, description, category);
   }

   protected LinearFlightModuleBase(String name, String description, Category category, int keybind) {
      super(name, description, category, keybind);
   }

   protected static boolean jumpHeld(LocalPlayer player) {
      return player.input != null && player.input.keyPresses.jump();
   }

   protected static boolean sneakHeld(LocalPlayer player) {
      return player.input != null && player.input.keyPresses.shift();
   }

   /** +1 while jump is held, -1 while sneak is held, 0 for neither or both. */
   protected static int verticalInput(LocalPlayer player) {
      return (jumpHeld(player) ? 1 : 0) - (sneakHeld(player) ? 1 : 0);
   }

   /** The horizontal velocity for the keys held, or zero when none are. */
   protected static Vec3 horizontal(LocalPlayer player, double speed) {
      Vec3 direction = MoveUtil.direction(player);
      return direction == null ? Vec3.ZERO : new Vec3(direction.x * speed, 0.0, direction.z * speed);
   }

   /** Horizontal steering at {@code horizontalSpeed} with jump and sneak as {@code verticalSpeed} up and down. */
   protected static Vec3 steering(LocalPlayer player, double horizontalSpeed, double verticalSpeed) {
      Vec3 flat = horizontal(player, horizontalSpeed);
      return new Vec3(flat.x, verticalInput(player) * verticalSpeed, flat.z);
   }
}
