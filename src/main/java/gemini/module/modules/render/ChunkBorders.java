package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.VersionBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class ChunkBorders extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Extra chunks drawn around yours", 0.0, 0.0, 3.0, 1.0));
   private final NumberSetting vertical = this.addSetting(
      new NumberSetting("Vertical Range", "Blocks above and below you that get lines", 48.0, 16.0, 256.0, 16.0)
   );
   private final BooleanSetting sections = this.addSetting(new BooleanSetting("Section Lines", "Rings every 16 blocks of height", true));
   private final ColorSetting edge = this.addSetting(new ColorSetting("Chunk Color", "Color of your chunk's edges", 210));
   private final ColorSetting inner = this.addSetting(new ColorSetting("Section Color", "Color of the 16-block rings", 120));
   private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the lines", 1.5, 0.5, 4.0, 0.5));

   public ChunkBorders() {
      super("Chunk Borders", "Draw chunk and section boundaries around you.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null) {
         int cx = player.chunkPosition().x();
         int cz = player.chunkPosition().z();
         int extra = this.radius.intValue();
         int py = Mth.floor(player.getY());
         int top = Math.min(VersionBridge.maxWorldY(level) + 1, py + this.vertical.intValue());
         int bottom = Math.max(VersionBridge.minWorldY(level), py - this.vertical.intValue());
         float stroke = this.width.floatValue();
         int edgeColor = this.edge.argb();
         int innerColor = this.inner.argb();
         int west = (cx - extra) * 16;
         int east = (cx + extra + 1) * 16;
         int north = (cz - extra) * 16;
         int south = (cz + extra + 1) * 16;

         for (int x = cx - extra; x <= cx + extra + 1; x++) {
            double wx = x * 16.0;
            boolean own = x == cx || x == cx + 1;

            for (int z = cz - extra; z <= cz + extra + 1; z++) {
               double wz = z * 16.0;
               boolean corner = own && (z == cz || z == cz + 1);
               Render3D.line(new Vec3(wx, bottom, wz), new Vec3(wx, top, wz), corner ? edgeColor : innerColor, corner ? stroke : stroke * 0.6F, false);
            }
         }

         int firstRing = Math.floorDiv(bottom, 16) * 16;

         for (int y = firstRing; y <= top; y += 16) {
            if (y >= bottom && (this.sections.get() || Math.abs(y - py) <= 16)) {
               ring(west, east, north, south, y, innerColor, stroke * 0.6F);
            }
         }

         ring(cx * 16, (cx + 1) * 16, cz * 16, (cz + 1) * 16, py, edgeColor, stroke);
      }
   }

   private static void ring(double west, double east, double north, double south, double y, int color, float width) {
      Vec3 a = new Vec3(west, y, north);
      Vec3 b = new Vec3(east, y, north);
      Vec3 c = new Vec3(east, y, south);
      Vec3 d = new Vec3(west, y, south);
      Render3D.line(a, b, color, width, false);
      Render3D.line(b, c, color, width, false);
      Render3D.line(c, d, color, width, false);
      Render3D.line(d, a, color, width, false);
   }
}
