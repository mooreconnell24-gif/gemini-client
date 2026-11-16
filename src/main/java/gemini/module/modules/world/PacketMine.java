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
      super("PacketMine", "Keeps mining the last block you started on after you let go of attack, until it breaks or is out of reach.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.targetPos != null && client.gameMode != null && !client.options.keyAttack.isDown()) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.targetPos = null;
   }

   /** The remembered block belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.targetPos = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         boolean attacking = client.options.keyAttack.isDown();
         if (attacking && client.hitResult instanceof BlockHitResult bhr && bhr.getType() == Type.BLOCK) {
            this.targetPos = bhr.getBlockPos().immutable();
         }

         if (this.targetPos != null) {
            if (!client.level.getBlockState(this.targetPos).isAir() && BlockPlacer.inReach(client.player, this.targetPos, 5.2)) {
               // While the button is held vanilla already mines the block under the crosshair; driving it here as
               // well would add a second dose of progress every tick. Take over only once the button is let go.
               if (!attacking) {
                  BlockBreaking.keep();
                  client.gameMode.continueDestroyBlock(this.targetPos, Direction.UP);
               }
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
