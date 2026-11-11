package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.VersionBridge;
import gemini.util.AreaScan;
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
   private final AreaScan scan = new AreaScan();
   private final List<BlockPos> building = new ArrayList<>();

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
      if (player == null || level == null) {
         return;
      }

      // The scan is spread over several ticks (about 0.3 ms each) instead of reading the whole radius at once.
      if (!this.scan.running() && this.wait-- <= 0) {
         this.wait = this.interval.intValue();
         this.building.clear();
         if (level.dimension() == Level.END) {
            this.holes.clear();
            return;
         }

         int r = this.radius.intValue();
         BlockPos center = player.blockPosition();
         int floor = VersionBridge.minWorldY(level);
         this.scan.start(new BlockPos(center.getX() - r, floor, center.getZ() - r), new BlockPos(center.getX() + r, floor, center.getZ() + r));
      }

      if (this.scan.running()) {
         boolean roof = this.netherRoof.get() && level.dimension() == Level.NETHER;
         boolean done = this.scan.tick(300_000L, cursor -> {
            if (level.hasChunkAt(cursor)) {
               if (level.getBlockState(cursor).isAir()) {
                  this.building.add(cursor.immutable());
               }

               if (roof) {
                  BlockPos top = new BlockPos(cursor.getX(), 127, cursor.getZ());
                  if (level.getBlockState(top).isAir() && !level.getBlockState(top.below()).is(Blocks.BEDROCK)) {
                     this.building.add(top);
                  }
               }
            }

            return this.building.size() < 2000;
         });
         if (done) {
            this.holes.clear();
            this.holes.addAll(this.building);
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

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.holes.clear();
      this.building.clear();
   }
}
