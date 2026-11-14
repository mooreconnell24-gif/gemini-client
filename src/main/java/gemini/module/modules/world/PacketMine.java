package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.util.BlockBreaking;
import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class PacketMine extends Module {
   private BlockPos targetPos;

   public PacketMine() {
      super("PacketMine", "Mine clicked blocks silently in the background.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.targetPos = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         if (client.options.keyAttack.isDown() && client.hitResult instanceof BlockHitResult bhr && bhr.getType() == Type.BLOCK) {
            this.targetPos = bhr.getBlockPos().immutable();
         }

         if (this.targetPos != null) {
            if (!client.level.getBlockState(this.targetPos).isAir() && BlockPlacer.inReach(client.player, this.targetPos, 5.2)) {
               BlockBreaking.keep();
               client.gameMode.continueDestroyBlock(this.targetPos, Direction.UP);
            } else {
               this.targetPos = null;
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.targetPos != null) {
         Render3D.block(this.targetPos, -16748289, true, true, 1.8F, 55, true);
      }
   }
}
