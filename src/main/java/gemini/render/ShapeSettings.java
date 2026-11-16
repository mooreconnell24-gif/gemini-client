package gemini.render;

import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShapeSettings {
   private final ModeSetting mode;
   private final NumberSetting width;
   private final NumberSetting fillOpacity;
   private final BooleanSetting throughWalls;

   public ShapeSettings(Module module, String defaultMode, boolean defaultThroughWalls) {
      this(module, defaultMode, defaultThroughWalls, () -> true);
   }

   public ShapeSettings(Module module, String defaultMode, boolean defaultThroughWalls, BooleanSupplier visible) {
      this.mode = module.addSetting(new ModeSetting("Shape", "Draw outlines only, filled faces only, or both", defaultMode, "Lines", "Sides", "Both").showIf(visible));
      this.width = module.addSetting(
         new NumberSetting("Line Width", "Thickness of the outlines", 2.0, 0.5, 6.0, 0.5).showIf(() -> visible.getAsBoolean() && !this.mode.is("Sides"))
      );
      this.fillOpacity = module.addSetting(
         new NumberSetting("Fill Opacity", "How solid the filled faces are", 28.0, 4.0, 160.0, 2.0)
            .showIf(() -> visible.getAsBoolean() && !this.mode.is("Lines"))
      );
      this.throughWalls = module.addSetting(
         new BooleanSetting("Through Walls", "Draw on top of blocks so it stays visible behind them", defaultThroughWalls).showIf(visible)
      );
   }

   public boolean lines() {
      return !this.mode.is("Sides");
   }

   public boolean fill() {
      return !this.mode.is("Lines");
   }

   public float width() {
      return this.width.floatValue();
   }

   public int fillAlpha() {
      return this.fillOpacity.intValue();
   }

   public boolean throughWalls() {
      return this.throughWalls.get();
   }

   public void box(AABB box, int color) {
      Render3D.box(box, color, this.lines(), this.fill(), this.width(), this.fillAlpha(), this.throughWalls());
   }

   /** A full block at integer coordinates; nothing is allocated for blocks that are off screen. */
   public void block(int x, int y, int z, int color) {
      Render3D.block(x, y, z, color, this.lines(), this.fill(), this.width(), this.fillAlpha(), this.throughWalls());
   }

   public void block(BlockPos pos, int color) {
      Render3D.block(pos, color, this.lines(), this.fill(), this.width(), this.fillAlpha(), this.throughWalls());
   }

   /** A box from its corners; nothing is allocated when it is off screen. */
   public void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {
      Render3D.box(minX, minY, minZ, maxX, maxY, maxZ, color, this.lines(), this.fill(), this.width(), this.fillAlpha(), this.throughWalls());
   }

   public void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
      Render3D.line(x1, y1, z1, x2, y2, z2, color, Math.max(1.0F, this.width()), this.throughWalls());
   }

   public void line(Vec3 from, Vec3 to, int color) {
      Render3D.line(from, to, color, Math.max(1.0F, this.width()), this.throughWalls());
   }
}
