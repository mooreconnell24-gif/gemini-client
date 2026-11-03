package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.VersionBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class TunnelEsp extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you to scan", 24.0, 8.0, 48.0, 4.0));
   private final NumberSetting below = this.addSetting(new NumberSetting("Depth", "Blocks below your feet to scan", 12.0, 0.0, 48.0, 2.0));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Scan Interval", "Ticks between scans", 20.0, 5.0, 100.0, 5.0));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 200));
   private final BooleanSetting fill = this.addSetting(new BooleanSetting("Fill", "Fill the highlighted blocks", true));
   private final List<BlockPos> found = new ArrayList<>();
   private int wait;

   public TunnelEsp() {
      super("Tunnel ESP", "Highlight 1x2 tunnels dug by players.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.found.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Level level = client.level;
      if (player != null && level != null && this.wait-- <= 0) {
         this.wait = this.interval.intValue();
         this.found.clear();
         int r = this.radius.intValue();
         BlockPos center = player.blockPosition();
         MutableBlockPos cursor = new MutableBlockPos();
         int yMin = Math.max(VersionBridge.minWorldY(level) + 1, center.getY() - this.below.intValue());
         int yMax = Math.min(VersionBridge.maxWorldY(level) - 2, center.getY() + 6);

         for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
               for (int y = yMin; y <= yMax; y++) {
                  cursor.set(center.getX() + x, y, center.getZ() + z);
                  if (tunnel(level, cursor)) {
                     this.found.add(cursor.immutable());
                     if (this.found.size() >= 1500) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean tunnel(Level level, BlockPos pos) {
      if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
         return false;
      } else if (solid(level, pos.below()) && solid(level, pos.above(2))) {
         boolean northSouth = solid(level, pos.east())
            && solid(level, pos.west())
            && solid(level, pos.above().east())
            && solid(level, pos.above().west())
            && level.getBlockState(pos.north()).isAir()
            && level.getBlockState(pos.south()).isAir();
         boolean eastWest = solid(level, pos.north())
            && solid(level, pos.south())
            && solid(level, pos.above().north())
            && solid(level, pos.above().south())
            && level.getBlockState(pos.east()).isAir()
            && level.getBlockState(pos.west()).isAir();
         return northSouth || eastWest;
      } else {
         return false;
      }
   }

   private static boolean solid(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && state.isSolidRender();
   }

   @Override
   public void onRender3D(float partialTick) {
      int argb = this.color.argb();

      for (BlockPos pos : this.found) {
         if (Render3D.visibleBlock(pos.getX(), pos.getY(), pos.getZ())) {
            Render3D.box(
               new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1), argb, true, this.fill.get(), 1.0F, 30, true
            );
         }
      }
   }

   @Override
   public String info() {
      return this.found.isEmpty() ? null : this.found.size() + " blocks";
   }
}
