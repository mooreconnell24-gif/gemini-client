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
import net.minecraft.world.level.block.Blocks;

public final class VoidEsp extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Blocks around you to scan", 32.0, 8.0, 96.0, 4.0));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Scan Interval", "Ticks between scans", 20.0, 5.0, 100.0, 5.0));
   private final BooleanSetting netherRoof = this.addSetting(new BooleanSetting("Nether Roof", "Also check the top bedrock layer in the Nether", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 300));
   private final List<BlockPos> holes = new ArrayList<>();
   private int wait;

   public VoidEsp() {
      super("Void ESP", "Highlight open holes in bedrock leading into the void.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.holes.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Level level = client.level;
      if (player != null && level != null && this.wait-- <= 0) {
         this.wait = this.interval.intValue();
         this.holes.clear();
         if (level.dimension() != Level.END) {
            int r = this.radius.intValue();
            BlockPos center = player.blockPosition();
            int floor = VersionBridge.minWorldY(level);
            MutableBlockPos cursor = new MutableBlockPos();

            for (int x = -r; x <= r; x++) {
               for (int z = -r; z <= r; z++) {
                  cursor.set(center.getX() + x, floor, center.getZ() + z);
                  if (level.hasChunkAt(cursor)) {
                     if (level.getBlockState(cursor).isAir()) {
                        this.holes.add(cursor.immutable());
                     }

                     if (this.netherRoof.get() && level.dimension() == Level.NETHER) {
                        cursor.setY(127);
                        if (level.getBlockState(cursor).isAir() && !level.getBlockState(cursor.below()).is(Blocks.BEDROCK)) {
                           this.holes.add(cursor.immutable());
                        }
                     }

                     if (this.holes.size() >= 2000) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      int argb = this.color.argb();

      for (BlockPos pos : this.holes) {
         if (Render3D.visibleBlock(pos.getX(), pos.getY(), pos.getZ())) {
            Render3D.block(pos, argb, true, true, 1.0F, 50, true);
         }
      }
   }

   @Override
   public String info() {
      return this.holes.isEmpty() ? null : this.holes.size() + " holes";
   }
}
