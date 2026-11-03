package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import gemini.util.ChunkScanner;
import gemini.util.VersionBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Search extends Module {
   private final ListSetting blocks = this.addSetting(
      new ListSetting(
         "Blocks",
         "Blocks to find in the loaded world",
         ListSetting.Kind.BLOCK,
         "minecraft:spawner",
         "minecraft:ancient_debris",
         "minecraft:diamond_ore",
         "minecraft:deepslate_diamond_ore"
      )
   );
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Chunks around you to search", 4.0, 1.0, 12.0, 1.0));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Scan Speed", "Chunk columns checked per tick", 4.0, 1.0, 24.0, 1.0));
   private final NumberSetting results = this.addSetting(new NumberSetting("Max Results", "Most blocks highlighted at once", 800.0, 50.0, 5000.0, 50.0));
   private final ModeSetting colors = this.addSetting(
      new ModeSetting("Colors", "One color per block type, or a single color", "Per Block", "Per Block", "Custom")
   );
   private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Highlight color", 200).showIf(() -> this.colors.is("Custom")));
   private final BooleanSetting tracers = this.addSetting(new BooleanSetting("Tracers", "Draw a line from your crosshair to every result", false));
   private final ShapeSettings shape = new ShapeSettings(this, "Both", true);
   private final Map<Block, Integer> palette = new HashMap<>();
   private ChunkScanner scanner;
   private int scanSpeed = -1;
   private int resultCap = -1;

   public Search() {
      super("Search", "Highlight selected blocks across loaded chunks.", Category.RENDER);
      this.blocks.onChange(this::reset);
      this.speed.onChange(this::reset);
      this.results.onChange(this::reset);
   }

   private void reset() {
      if (this.scanner != null) {
         this.scanner.clear();
      }

      this.scanner = null;
      this.palette.clear();
   }

   @Override
   public void onDisable() {
      this.reset();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         if (this.scanner == null) {
            this.scanner = new ChunkScanner(state -> this.blocks.blocks().contains(state.getBlock()), this.speed.intValue(), this.results.intValue());
         }

         if (!this.blocks.isEmpty()) {
            this.scanner.tick(client.level, client.player.chunkPosition().x(), client.player.chunkPosition().z(), this.radius.intValue());
         }
      }
   }

   private int colorOf(Block block) {
      return this.colors.is("Custom") ? this.custom.argb() : this.palette.computeIfAbsent(block, key -> {
         int hash = BuiltInRegistries.BLOCK.getKey(key).toString().hashCode();
         return ColorSetting.hueColor((hash >>> 3 & 1023) / 1023.0F, 0.75F, 1.0F);
      });
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (this.scanner != null && client.level != null && client.player != null) {
         Level level = client.level;
         Vec3 origin = Render3D.crosshair();
         MutableBlockPos cursor = new MutableBlockPos();
         Set<Block> wanted = this.blocks.blocks();
         int bottom = VersionBridge.minWorldY(level);
         int top = VersionBridge.maxWorldY(level) + 1;
         this.scanner
            .forEach((chunkX, chunkZ) -> Render3D.visible(chunkX << 4, bottom, chunkZ << 4, (chunkX << 4) + 16, top, (chunkZ << 4) + 16), packed -> {
               cursor.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
               Block block = level.getBlockState(cursor).getBlock();
               if (wanted.contains(block)) {
                  int color = this.colorOf(block);
                  this.shape
                     .box(new AABB(cursor.getX(), cursor.getY(), cursor.getZ(), cursor.getX() + 1.0, cursor.getY() + 1.0, cursor.getZ() + 1.0), color);
                  if (this.tracers.get()) {
                     Render3D.line(origin, Vec3.atCenterOf(cursor), color, 1.0F, true);
                  }
               }
            });
      }
   }

   @Override
   public String info() {
      return this.scanner == null ? null : Integer.toString(this.scanner.count());
   }
}
