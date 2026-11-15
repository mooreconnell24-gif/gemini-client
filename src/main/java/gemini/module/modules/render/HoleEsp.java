package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Holes;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class HoleEsp extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal scan radius for safe holes", 8.0, 3.0, 16.0, 1.0));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Render holes through blocks", true));
   private final List<BlockPos> cached = new ArrayList<>();
   /** Colour of each cached hole, decided when it was found so drawing does not read the world again every frame. */
   private final IntArrayList colors = new IntArrayList();
   private int wait;

   public HoleEsp() {
      super("HoleESP", "Highlight safe Bedrock and Obsidian PvP holes.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.cached.clear();
      this.colors.clear();
   }

   @Override
   public void onWorldChange() {
      this.cached.clear();
      this.colors.clear();
      this.wait = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && this.wait-- <= 0) {
         this.wait = 6;
         this.cached.clear();
         this.colors.clear();
         for (BlockPos pos : Holes.around(client.level, client.player.blockPosition(), this.radius.intValue(), 3)) {
            Holes.Safety safety = Holes.classify(client.level, pos);
            if (safety != Holes.Safety.NONE) {
               this.cached.add(pos);
               this.colors.add(switch (safety) {
                  case BEDROCK -> -11086174;
                  case OBSIDIAN -> -6467875;
                  default -> -16722689;
               });
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      boolean onTop = this.throughWalls.get();
      for (int i = 0; i < this.cached.size(); i++) {
         BlockPos pos = this.cached.get(i);
         Render3D.box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 0.25, pos.getZ() + 1.0, this.colors.getInt(i), true, true, 1.8F, 55, onTop);
      }
   }
}
