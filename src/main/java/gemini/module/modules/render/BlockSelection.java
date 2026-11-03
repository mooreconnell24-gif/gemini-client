package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.render.ShapeSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockSelection extends Module {
   private final ModeSetting colors = this.addSetting(new ModeSetting("Colors", "Fixed color or an animated rainbow", "Custom", "Custom", "Rainbow"));
   private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Outline color", 175).showIf(() -> this.colors.is("Custom")));
   private final BooleanSetting exactShape = this.addSetting(
      new BooleanSetting("Exact Shape", "Follow stairs, slabs and fences instead of a plain", true)
   );
   private final ShapeSettings shape = new ShapeSettings(this, "Both", false);

   public BlockSelection() {
      super("Block Selection", "Recolor the outline of the block you are looking at.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
         BlockPos pos = hit.getBlockPos();
         BlockState state = client.level.getBlockState(pos);
         if (!state.isAir()) {
            int color = this.colors.is("Rainbow")
               ? ColorSetting.hueColor((float)(System.currentTimeMillis() % 4000L) / 4000.0F, 0.7F, 1.0F)
               : this.custom.argb();
            if (!this.exactShape.get()) {
               this.shape.block(pos, color);
            } else {
               VoxelShape voxel = state.getShape(client.level, pos, CollisionContext.of(client.player));
               if (voxel.isEmpty()) {
                  this.shape.block(pos, color);
               } else {
                  for (AABB box : voxel.toAabbs()) {
                     this.shape.box(box.move(pos).inflate(0.002), color);
                  }
               }
            }
         }
      }
   }
}
