package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.AreaScan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A box and a "Bed [cover]" tag over each bed near you. Beds are found by a scan spread over ticks; the tag text
 * (what sits on top of the bed) is checked once a tick and rebuilt only when that block changes, so a frame draws
 * stored strings at stored coordinates.
 */
public final class BedPlates extends Module {
   private static final int MAX_BEDS = 12;
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Scan radius for beds, in blocks (6 up and down)", 24.0, 8.0, 48.0, 4.0));
   private final List<BlockPos> beds = new ArrayList<>();
   /** Parallel to {@link #beds}: the block on top of each bed, and the tag built from it. */
   private final List<Block> covers = new ArrayList<>();
   private final List<String> tags = new ArrayList<>();
   private int wait;
   private final AreaScan scan = new AreaScan();
   private final List<BlockPos> building = new ArrayList<>();
   private final MutableBlockPos above = new MutableBlockPos();

   public BedPlates() {
      super("BedPlates", "Show a box and a tag naming the block that covers each nearby bed.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.forget();
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.forget();
   }

   private void forget() {
      this.scan.stop();
      this.building.clear();
      this.beds.clear();
      this.covers.clear();
      this.tags.clear();
      this.wait = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      if (client.player == null || level == null) {
         return;
      }

      if (!this.scan.running() && this.wait-- <= 0) {
         this.wait = 25;
         this.building.clear();
         int r = this.radius.intValue();
         BlockPos c = client.player.blockPosition();
         this.scan.start(c.offset(-r, -6, -r), c.offset(r, 6, r));
      }

      if (this.scan.running() && this.scan.tick(250_000L, pos -> {
         BlockState state = level.getBlockState(pos);
         // One tag per bed: only the head half counts.
         if (state.is(BlockTags.BEDS) && (!state.hasProperty(BlockStateProperties.BED_PART) || state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD)) {
            this.building.add(pos.immutable());
         }

         return this.building.size() < MAX_BEDS;
      })) {
         this.beds.clear();
         this.beds.addAll(this.building);
         this.covers.clear();
         this.tags.clear();
         for (int i = 0; i < this.beds.size(); i++) {
            this.covers.add(null);
            this.tags.add("");
         }
      }

      for (int i = 0; i < this.beds.size(); i++) {
         BlockPos bed = this.beds.get(i);
         this.above.set(bed.getX(), bed.getY() + 1, bed.getZ());
         Block cover = level.getBlockState(this.above).getBlock();
         if (cover != this.covers.get(i)) {
            this.covers.set(i, cover);
            this.tags.set(i, "Bed [" + cover.getName().getString() + "]");
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      for (int i = 0; i < this.beds.size(); i++) {
         BlockPos bed = this.beds.get(i);
         Render3D.block(bed.getX(), bed.getY(), bed.getZ(), -16722689, true, true, 1.5F, 40, true);
         Render3D.text(this.tags.get(i), bed.getX() + 0.5, bed.getY() + 1.65, bed.getZ() + 0.5, -1, 0.8F, true);
      }
   }
}
