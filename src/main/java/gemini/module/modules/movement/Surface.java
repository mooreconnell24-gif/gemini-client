package gemini.module.modules.movement;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One key to get out of a cave: teleports you straight up to the surface. In dimensions with a roof (the Nether)
 * it goes up to the next open space above instead of onto the roof.
 */
public final class Surface extends Module {
   public Surface() {
      super("Surface", "Teleport straight up to the surface, or to the next open level under a roof.", Category.MOVEMENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      this.ascend();
      this.setEnabled(false, false);
   }

   private void ascend() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player == null || level == null) {
         return;
      }

      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
         return;
      }

      int x = player.getBlockX();
      int z = player.getBlockZ();
      int target = level.dimensionType().hasCeiling() ? nextOpenLevel(level, x, player.getBlockY(), z) : level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
      if (target == Integer.MIN_VALUE || target <= player.getBlockY()) {
         CommandManager.feedback("§bSurface §7there is nothing above you to go up to.");
      } else if (SpCommands.teleport(level.dimension().identifier().toString(), x + 0.5, target, z + 0.5)) {
         CommandManager.feedback("§bSurface §7up §f" + (target - player.getBlockY()) + " §7blocks.");
      }
   }

   /** The feet height of the first two-block gap with solid ground under it above {@code fromY}, or MIN_VALUE. */
   private static int nextOpenLevel(ClientLevel level, int x, int fromY, int z) {
      MutableBlockPos cursor = new MutableBlockPos();
      int top = level.getMinY() + level.getHeight() - 2;
      boolean passedSolid = false;
      for (int y = fromY + 2; y <= top; y++) {
         BlockState ground = level.getBlockState(cursor.set(x, y - 1, z));
         boolean solid = !ground.getCollisionShape(level, cursor).isEmpty();
         passedSolid |= solid;
         if (passedSolid && solid && open(level, cursor.set(x, y, z)) && open(level, cursor.set(x, y + 1, z))) {
            return y;
         }
      }

      return Integer.MIN_VALUE;
   }

   private static boolean open(ClientLevel level, MutableBlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
   }
}
