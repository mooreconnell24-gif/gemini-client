package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class LightOverlay extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Horizontal radius", 8.0, 2.0, 16.0, 1.0));
   private final NumberSetting vertical = this.addSetting(new NumberSetting("Vertical Range", "How far above and below you to look", 3.0, 1.0, 8.0, 1.0));
   private final NumberSetting interval = this.addSetting(new NumberSetting("Refresh", "Ticks between rescans", 10.0, 2.0, 40.0, 1.0));
   private final NumberSetting blockLimit = this.addSetting(
      new NumberSetting("Block Light Limit", "Mobs spawn at or below this block light", 0.0, 0.0, 14.0, 1.0)
   );
   private final NumberSetting skyLimit = this.addSetting(
      new NumberSetting("Sky Light Limit", "Mobs spawn at or below this light level right now", 7.0, 0.0, 14.0, 1.0)
   );
   private final BooleanSetting night = this.addSetting(new BooleanSetting("Show Night Spots", "Also mark places that only become dangerous at night", true));
   private final ColorSetting nowColor = this.addSetting(new ColorSetting("Danger Color", "Spawnable right now", 0));
   private final ColorSetting nightColor = this.addSetting(new ColorSetting("Night Color", "Spawnable after dark", 52));
   private final BooleanSetting cross = this.addSetting(new BooleanSetting("Cross", "Draw an X on the floor", true));
   private final BooleanSetting fill = this.addSetting(new BooleanSetting("Fill", "Tint the floor tile", true));
   private final NumberSetting limit = this.addSetting(new NumberSetting("Max Marks", "Most markers drawn at once", 400.0, 50.0, 1500.0, 50.0));
   private final List<BlockPos> marks = new ArrayList<>();
   private final List<Boolean> dangerous = new ArrayList<>();
   private int countdown;

   public LightOverlay() {
      super("Light Overlay", "Mark floor tiles where hostile mobs can spawn.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.marks.clear();
      this.dangerous.clear();
      this.countdown = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (client.player != null && level != null && this.countdown-- <= 0) {
         this.countdown = this.interval.intValue();
         this.marks.clear();
         this.dangerous.clear();
         BlockPos origin = client.player.blockPosition();
         int h = this.range.intValue();
         int v = this.vertical.intValue();
         int maxMarks = this.limit.intValue();
         MutableBlockPos cursor = new MutableBlockPos();
         MutableBlockPos floor = new MutableBlockPos();

         for (int dx = -h; dx <= h && this.marks.size() < maxMarks; dx++) {
            for (int dz = -h; dz <= h && this.marks.size() < maxMarks; dz++) {
               for (int dy = -v; dy <= v; dy++) {
                  cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                  BlockState here = level.getBlockState(cursor);
                  if (here.getCollisionShape(level, cursor).isEmpty() && here.getFluidState().isEmpty()) {
                     floor.setWithOffset(cursor, Direction.DOWN);
                     BlockState below = level.getBlockState(floor);
                     if (below.isFaceSturdy(level, floor, Direction.UP) && !below.is(Blocks.BEDROCK)) {
                        int block = level.getBrightness(LightLayer.BLOCK, cursor);
                        if (block <= this.blockLimit.intValue()) {
                           int effective = Math.min(level.getMaxLocalRawBrightness(cursor), 15);
                           boolean now = effective <= this.skyLimit.intValue();
                           if (now || this.night.get()) {
                              this.marks.add(cursor.immutable());
                              this.dangerous.add(now);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      int red = this.nowColor.argb();
      int yellow = this.nightColor.argb();

      for (int i = 0; i < this.marks.size(); i++) {
         Render3D.floorMark(
            this.marks.get(i), 0.02, this.dangerous.get(i) ? red : yellow, this.cross.get(), this.fill.get(), 1.5F, this.dangerous.get(i) ? 90 : 60, false
         );
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.marks.clear();
      this.dangerous.clear();
   }
}
