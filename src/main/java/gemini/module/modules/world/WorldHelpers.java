package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.util.BlockPlacer;
import gemini.util.Hands;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the world modules (formerly statics on the module container classes). */
public final class WorldHelpers {
   private WorldHelpers() {
   }

   static boolean withinReach(LocalPlayer player, Entity entity, double reach) {
      return entity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
   }

   static Direction faceToward(LocalPlayer player, BlockPos pos) {
      Vec3 eye = player.getEyePosition();
      return Direction.getApproximateNearest(eye.x - (pos.getX() + 0.5), eye.y - (pos.getY() + 0.5), eye.z - (pos.getZ() + 0.5));
   }

}
