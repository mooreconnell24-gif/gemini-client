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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockSelection extends Module {
   private static final double GROW = 0.002;
   private final ModeSetting colors = this.addSetting(new ModeSetting("Colors", "Fixed color or an animated rainbow", "Custom", "Custom", "Rainbow"));
   private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Outline color", 175).showIf(() -> this.colors.is("Custom")));
   private final BooleanSetting exactShape = this.addSetting(
      new BooleanSetting("Exact Shape", "Follow stairs, slabs and fences instead of a plain full-block box", true)
   );
   private final ShapeSettings shape = new ShapeSettings(this, "Both", false);
   private final BooleanSetting hideVanilla = this.addSetting(
      new BooleanSetting("Hide Vanilla", "Hide the vanilla black outline so only this one is drawn, instead of drawing on top of it", true)
   );
   /** Read by the level renderer every frame; written on the client thread. */
   private static volatile boolean replacingVanilla;
   /** The shape of the block last looked at; looked up again only when the block or its state changes. */
   private BlockPos shapePos;
   private BlockState shapeState;
   private VoxelShape cachedShape;
   private int drawX;
   private int drawY;
   private int drawZ;
   private int drawColor;
   private final Shapes.DoubleLineConsumer drawBox = (minX, minY, minZ, maxX, maxY, maxZ) -> this.shape.box(
      this.drawX + minX - GROW, this.drawY + minY - GROW, this.drawZ + minZ - GROW, this.drawX + maxX + GROW, this.drawY + maxY + GROW, this.drawZ + maxZ + GROW, this.drawColor
   );

   public BlockSelection() {
      super("Block Selection", "Recolor the outline of the block you are looking at.", Category.RENDER);
   }

   /** Whether the vanilla block outline should be skipped this frame. */
   public static boolean replacesVanilla() {
      return replacingVanilla;
   }

   @Override
   public void onEnable() {
      replacingVanilla = this.hideVanilla.get();
   }

   @Override
   public void onTick() {
      replacingVanilla = this.hideVanilla.get();
   }

   @Override
   public void onDisable() {
      replacingVanilla = false;
      this.shapePos = null;
      this.shapeState = null;
      this.cachedShape = null;
   }

   @Override
   public void onWorldChange() {
      this.onDisable();
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
               if (state != this.shapeState || !pos.equals(this.shapePos)) {
                  this.shapePos = pos.immutable();
                  this.shapeState = state;
                  this.cachedShape = state.getShape(client.level, pos, CollisionContext.of(client.player));
               }

               if (this.cachedShape.isEmpty()) {
                  this.shape.block(pos, color);
               } else {
                  this.drawX = pos.getX();
                  this.drawY = pos.getY();
                  this.drawZ = pos.getZ();
                  this.drawColor = color;
                  this.cachedShape.forAllBoxes(this.drawBox);
               }
            }
         }
      }
   }
}
