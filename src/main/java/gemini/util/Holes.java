package gemini.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Holes {
   private static final Direction[] SIDES = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   private Holes() {
   }

   private static boolean passable(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.isAir() || state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
   }

   private static boolean resistant(BlockState state) {
      return state.is(Blocks.BEDROCK) || state.getBlock().getExplosionResistance() >= 600.0F;
   }

   public static Holes.Safety classify(Level level, BlockPos pos) {
      if (passable(level, pos) && passable(level, pos.above())) {
         boolean bedrock = false;
         boolean other = false;
         MutableBlockPos cursor = new MutableBlockPos();
         BlockState floor = level.getBlockState(cursor.setWithOffset(pos, Direction.DOWN));
         if (!resistant(floor)) {
            return Holes.Safety.NONE;
         } else {
            if (floor.is(Blocks.BEDROCK)) {
               bedrock = true;
            } else {
               other = true;
            }

            for (Direction side : SIDES) {
               BlockState wall = level.getBlockState(cursor.setWithOffset(pos, side));
               if (!resistant(wall)) {
                  return Holes.Safety.NONE;
               }

               if (wall.is(Blocks.BEDROCK)) {
                  bedrock = true;
               } else {
                  other = true;
               }
            }

            if (bedrock && other) {
               return Holes.Safety.MIXED;
            } else {
               return bedrock ? Holes.Safety.BEDROCK : Holes.Safety.OBSIDIAN;
            }
         }
      } else {
         return Holes.Safety.NONE;
      }
   }

   public static boolean isHole(Level level, BlockPos pos) {
      return classify(level, pos) != Holes.Safety.NONE;
   }

   public static List<BlockPos> around(Level level, BlockPos center, int horizontal, int vertical) {
      List<BlockPos> found = new ArrayList<>();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -horizontal; dx <= horizontal; dx++) {
         for (int dz = -horizontal; dz <= horizontal; dz++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
               cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
               if (isHole(level, cursor)) {
                  found.add(cursor.immutable());
               }
            }
         }
      }

      found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
      return found;
   }

   public static enum Safety {
      NONE,
      OBSIDIAN,
      MIXED,
      BEDROCK;
   }
}
