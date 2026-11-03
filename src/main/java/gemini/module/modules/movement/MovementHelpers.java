package gemini.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Shared helpers for the movement modules (formerly statics on the module container classes). */
public final class MovementHelpers {
   private MovementHelpers() {
   }

   static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null && client.level != null && client.gameMode != null && !player.isSpectator() && !player.isDeadOrDying();
   }

   static boolean airborne(LocalPlayer player) {
      return !player.onGround()
         && !player.getAbilities().flying
         && !player.isFallFlying()
         && !player.isInWater()
         && !player.isInLava()
         && !player.onClimbable()
         && !player.isPassenger();
   }

   static boolean passable(Level level, BlockPos pos) {
      return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   static double groundDistance(Level level, LocalPlayer player, int scan) {
      for (int i = 0; i <= scan; i++) {
         BlockPos below = BlockPos.containing(player.getX(), player.getY() - i - 0.01, player.getZ());
         if (!passable(level, below)) {
            return Math.max(0.0, player.getY() - (below.getY() + 1.0));
         }
      }

      return -1.0;
   }
}
