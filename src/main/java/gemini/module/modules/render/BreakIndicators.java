package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class BreakIndicators extends Module {
   private final BooleanSetting percentage = this.addSetting(new BooleanSetting("Percentage", "Show mining completion percentage in 3D", true));
   private BlockPos miningPos;
   private float progress;

   public BreakIndicators() {
      super("BreakIndicators", "Show a 3D progress box and % on mined blocks.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.miningPos = null;
      this.progress = 0.0F;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && client.gameMode != null) {
         if (client.gameMode.isDestroying() && client.hitResult instanceof BlockHitResult bhr && bhr.getType() == Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            if (state.isAir()) {
               this.miningPos = null;
               this.progress = 0.0F;
               return;
            }

            if (!pos.equals(this.miningPos)) {
               this.miningPos = pos.immutable();
               this.progress = 0.0F;
            }

            this.progress = Math.min(1.0F, this.progress + state.getDestroyProgress(client.player, client.level, pos));
         } else {
            this.miningPos = null;
            this.progress = 0.0F;
         }
      } else {
         this.miningPos = null;
         this.progress = 0.0F;
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.miningPos != null && this.progress > 0.01F) {
         float p = Mth.clamp(this.progress, 0.05F, 1.0F);
         int color = ColorSetting.hueColor(p * 0.33F, 0.85F, 1.0F);
         double half = 0.5 * p;
         Vec3 c = Vec3.atCenterOf(this.miningPos);
         AABB box = new AABB(c.x - half, c.y - half, c.z - half, c.x + half, c.y + half, c.z + half);
         Render3D.boxGlow(box, color, 1.8F, 55, true);
         if (this.percentage.get()) {
            Render3D.text(Math.round(p * 100.0F) + "%", c.add(0.0, 0.65, 0.0), color, 0.85F, true);
         }
      }
   }
}
