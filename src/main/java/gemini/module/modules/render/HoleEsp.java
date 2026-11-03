package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Holes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class HoleEsp extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal scan radius for safe holes", 8.0, 3.0, 16.0, 1.0));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Render holes through blocks", true));
   private final List<BlockPos> cached = new ArrayList<>();
   private int wait;

   public HoleEsp() {
      super("HoleESP", "Highlight safe Bedrock and Obsidian PvP holes.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.cached.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && this.wait-- <= 0) {
         this.wait = 6;
         this.cached.clear();
         this.cached.addAll(Holes.around(client.level, client.player.blockPosition(), this.radius.intValue(), 3));
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null) {
         for (BlockPos pos : this.cached) {
            Holes.Safety safety = Holes.classify(client.level, pos);
            if (safety != Holes.Safety.NONE) {
               int color = switch (safety) {
                  case BEDROCK -> -11086174;
                  case OBSIDIAN -> -6467875;
                  default -> -16722689;
               };
               AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 0.25, pos.getZ() + 1.0);
               Render3D.box(box, color, true, true, 1.8F, 55, this.throughWalls.get());
            }
         }
      }
   }
}
