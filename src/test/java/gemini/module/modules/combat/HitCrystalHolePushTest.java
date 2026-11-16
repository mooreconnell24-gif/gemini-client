package gemini.module.modules.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Hit Crystal's knockback landing estimate and Hole Push's piston geometry. */
class HitCrystalHolePushTest {
   @Test
   void knockbackStrengthAddsLevelsAndSprint() {
      assertEquals(0.4, HitCrystal.knockbackStrength(0, false), 1e-9);
      assertEquals(0.9, HitCrystal.knockbackStrength(0, true), 1e-9);
      assertEquals(1.4, HitCrystal.knockbackStrength(2, false), 1e-9);
      assertEquals(1.9, HitCrystal.knockbackStrength(2, true), 1e-9);
   }

   @Test
   void landingFollowsTheAttackersFacing() {
      // Yaw 0 faces south (+Z), 90 west (-X), 180 north, -90 east; travel is two blocks per unit of strength.
      Vec3 south = HitCrystal.landingOffset(0.0F, 1.0);
      assertEquals(0.0, south.x, 1e-4);
      assertEquals(2.0, south.z, 1e-4);
      assertEquals(0.0, south.y, 1e-9, "the estimate stays on the target's level");
      Vec3 west = HitCrystal.landingOffset(90.0F, 0.5);
      assertEquals(-1.0, west.x, 1e-4);
      assertEquals(0.0, west.z, 1e-4);
      Vec3 north = HitCrystal.landingOffset(180.0F, 0.9);
      assertEquals(-1.8, north.z, 1e-4);
      Vec3 east = HitCrystal.landingOffset(-90.0F, HitCrystal.knockbackStrength(1, true));
      assertEquals(2.8, east.x, 1e-4);
   }

   @Test
   void pistonAndDestinationSitAtHeadHeightOnOppositeSides() {
      BlockPos feet = new BlockPos(10, 64, -5);
      for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
         BlockPos piston = HolePush.pistonSpot(feet, side);
         BlockPos destination = HolePush.destinationSpot(feet, side);
         assertEquals(65, piston.getY());
         assertEquals(65, destination.getY());
         assertEquals(feet.above().relative(side), piston);
         // The head is exactly between the piston and where it is pushed to.
         assertEquals(feet.above().getX() * 2, piston.getX() + destination.getX());
         assertEquals(feet.above().getZ() * 2, piston.getZ() + destination.getZ());
      }
   }

   @Test
   void powerNeverGoesInFrontOfThePiston() {
      for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
         Direction[] options = HolePush.powerDirections(side);
         assertEquals(side, options[0], "behind the piston is tried first");
         assertEquals(Direction.UP, options[1]);
         assertEquals(Direction.DOWN, options[options.length - 1]);
         for (Direction dir : options) {
            assertNotEquals(side.getOpposite(), dir, "the front is where the piston head extends");
         }
      }

      assertArrayEquals(
         new Direction[]{Direction.NORTH, Direction.UP, Direction.EAST, Direction.WEST, Direction.DOWN}, HolePush.powerDirections(Direction.NORTH)
      );
   }

   @Test
   void torchesOnlyStand() {
      assertFalse(HolePush.torchSide(Direction.DOWN));
      assertTrue(HolePush.torchSide(Direction.UP));
      assertTrue(HolePush.torchSide(Direction.EAST));
   }
}
