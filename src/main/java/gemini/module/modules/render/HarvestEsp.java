package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import gemini.util.ChunkScanner;
import gemini.util.InfoText;
import gemini.util.VersionBridge;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Highlights crops that are ready to harvest across the loaded chunks around you: fully grown crops, nether wart
 * and cocoa, sweet and glow berries that can be picked, and melons and pumpkins hanging off a stem. Optionally
 * marks the ones still growing in a second colour. Chunks are scanned in the background by {@link ChunkScanner}
 * and rescanned when a block in them changes, so growth and harvests show up on their own.
 */
public final class HarvestEsp extends Module {
   private static final int NOT_CROP = 0;
   private static final int GROWING = 1;
   private static final int RIPE = 2;
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "How far around you to search, in chunks", 4.0, 1.0, 10.0, 1.0));
   private final BooleanSetting crops = this.addSetting(new BooleanSetting("Crops", "Mark wheat, carrots, potatoes, beetroot and torchflowers", true));
   private final BooleanSetting wart = this.addSetting(new BooleanSetting("Nether Wart", "Mark fully grown nether wart", true));
   private final BooleanSetting cocoa = this.addSetting(new BooleanSetting("Cocoa", "Mark ripe cocoa pods", true));
   private final BooleanSetting berries = this.addSetting(new BooleanSetting("Berries", "Mark sweet berry bushes and glow berry vines you can pick", true));
   private final BooleanSetting gourds = this.addSetting(new BooleanSetting("Melons & Pumpkins", "Mark melons and pumpkins grown from a stem", true));
   private final BooleanSetting growing = this.addSetting(new BooleanSetting("Show Growing", "Also mark crops that are not ripe yet, in their own colour", false));
   private final ColorSetting ripeColor = this.addSetting(new ColorSetting("Ripe Color", "Colour of crops ready to harvest", 110));
   private final ColorSetting growingColor = this.addSetting(new ColorSetting("Growing Color", "Colour of crops still growing", 45).showIf(this.growing::get));
   private final ShapeSettings shape = new ShapeSettings(this, "Both", false);
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
   private final ChunkScanner.ColumnFilter onScreen = this::columnVisible;
   private final LongConsumer drawer = this::draw;
   private final InfoText infoText = new InfoText();
   private ChunkScanner scanner;
   private ClientLevel drawLevel;
   private int bottom;
   private int top;
   private int ripeArgb;
   private int growingArgb;

   public HarvestEsp() {
      super("Harvest ESP", "Highlights ripe crops, nether wart, cocoa, berries, melons and pumpkins around you.", Category.RENDER);
      Runnable reset = this::reset;
      this.crops.onChange(reset);
      this.wart.onChange(reset);
      this.cocoa.onChange(reset);
      this.berries.onChange(reset);
      this.gourds.onChange(reset);
      this.growing.onChange(reset);
   }

   private void reset() {
      if (this.scanner != null) {
         this.scanner.clear();
      }

      this.scanner = null;
   }

   @Override
   public void onDisable() {
      this.reset();
      this.drawLevel = null;
   }

   @Override
   public void onWorldChange() {
      this.reset();
      this.drawLevel = null;
   }

   /** How ripe a block is, honouring the type switches: {@link #NOT_CROP}, {@link #GROWING} or {@link #RIPE}. */
   private int ripeness(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof CropBlock crop) {
         return !this.crops.get() ? NOT_CROP : (crop.isMaxAge(state) ? RIPE : GROWING);
      } else if (block instanceof NetherWartBlock) {
         return !this.wart.get() ? NOT_CROP : (state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? RIPE : GROWING);
      } else if (block instanceof CocoaBlock) {
         return !this.cocoa.get() ? NOT_CROP : (state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE ? RIPE : GROWING);
      } else if (block instanceof SweetBerryBushBlock) {
         // Bushes give berries from age 2 on.
         return !this.berries.get() ? NOT_CROP : (state.getValue(SweetBerryBushBlock.AGE) >= 2 ? RIPE : GROWING);
      } else if (block instanceof CaveVinesBlock || block instanceof CaveVinesPlantBlock) {
         return !this.berries.get() ? NOT_CROP : (CaveVines.hasGlowBerries(state) ? RIPE : GROWING);
      } else if (block instanceof AttachedStemBlock) {
         return this.gourds.get() ? RIPE : NOT_CROP;
      } else {
         return NOT_CROP;
      }
   }

   private boolean wanted(BlockState state) {
      int ripeness = this.ripeness(state);
      return ripeness == RIPE || ripeness == GROWING && this.growing.get() && !(state.getBlock() instanceof CaveVinesPlantBlock || state.getBlock() instanceof CaveVinesBlock);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         if (this.scanner == null) {
            this.scanner = new ChunkScanner(this::wanted, 4, 4000);
         }

         this.scanner.tick(client.level, client.player.chunkPosition().x(), client.player.chunkPosition().z(), this.radius.intValue());
      }
   }

   private boolean columnVisible(int chunkX, int chunkZ) {
      return Render3D.visible(chunkX << 4, this.bottom, chunkZ << 4, (chunkX << 4) + 16, this.top, (chunkZ << 4) + 16);
   }

   private void draw(long packed) {
      this.cursor.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
      BlockState state = this.drawLevel.getBlockState(this.cursor);
      // Checked again: a crop harvested since the last scan of its chunk must not stay marked.
      int ripeness = this.ripeness(state);
      if (ripeness == NOT_CROP || ripeness == GROWING && !this.growing.get()) {
         return;
      }

      if (state.getBlock() instanceof AttachedStemBlock) {
         Direction facing = state.getValue(AttachedStemBlock.FACING);
         this.cursor.move(facing);
      }

      this.shape.block(this.cursor.getX(), this.cursor.getY(), this.cursor.getZ(), ripeness == RIPE ? this.ripeArgb : this.growingArgb);
   }

   @Override
   public void onRender3D(float partialTick) {
      ClientLevel level = Minecraft.getInstance().level;
      if (this.scanner == null || level == null) {
         return;
      }

      this.drawLevel = level;
      this.bottom = VersionBridge.minWorldY(level);
      this.top = VersionBridge.maxWorldY(level) + 1;
      this.ripeArgb = this.ripeColor.argb();
      this.growingArgb = this.growingColor.argb();
      this.scanner.forEach(this.onScreen, this.drawer);
   }

   @Override
   public String info() {
      return this.scanner == null ? null : this.infoText.whole(this.scanner.count(), "");
   }
}
