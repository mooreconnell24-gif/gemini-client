package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.Vec3;

public final class BedPlates extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Scan radius for beds", 24.0, 8.0, 48.0, 4.0));
   private final List<BlockPos> beds = new ArrayList<>();
   private int wait;

   public BedPlates() {
      super("BedPlates", "Show floating 3D defense tags above nearby beds.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.beds.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && this.wait-- <= 0) {
         this.wait = 25;
         this.beds.clear();
         int r = this.radius.intValue();
         BlockPos c = client.player.blockPosition();

         for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, 6, r))) {
            if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
               this.beds.add(pos.immutable());
               if (this.beds.size() >= 12) {
                  break;
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null) {
         for (BlockPos bed : this.beds) {
            String cover = client.level.getBlockState(bed.above()).getBlock().getName().getString();
            Render3D.block(bed, -16722689, true, true, 1.5F, 40, true);
            Render3D.text("Bed [" + cover + "]", Vec3.atCenterOf(bed).add(0.0, 1.15, 0.0), -1, 0.8F, true);
         }
      }
   }
}
