package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Draws the borders between biomes around you as low fences at your height. Biomes are stored per 4x4x4 cell, so
 * the loaded chunks around you are sampled on that grid at your feet's level and the changes between neighbouring
 * cells become merged line segments ({@link BiomeEdges}). The grid is only sampled again when you move to another
 * chunk or block level, change a setting, or every two seconds for chunks that loaded since.
 */
public final class BiomeBorder extends Module {
   private static final int REFRESH_TICKS = 40;
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Chunks around you to show borders in", 3.0, 1.0, 8.0, 1.0));
   private final NumberSetting height = this.addSetting(
      new NumberSetting("Wall Height", "How tall the border fence is drawn (0: a line on the ground only)", 2.0, 0.0, 16.0, 0.5)
   );
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Border line colour", 280));
   private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the border lines", 1.5, 1.0, 4.0, 0.5));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Show borders behind blocks too", false));
   private final Reference2IntOpenHashMap<Biome> biomeIds = new Reference2IntOpenHashMap<>();
   private int[] ids = new int[0];
   private int[] segments = new int[0];
   private int segmentCount;
   private int originX;
   private int originZ;
   private int lineY;
   private int lastChunkX = Integer.MIN_VALUE;
   private int lastChunkZ = Integer.MIN_VALUE;
   private int lastBlockY = Integer.MIN_VALUE;
   private int lastRange = -1;
   private int countdown;

   public BiomeBorder() {
      super("Biome Border", "Show where one biome ends and the next begins, as fences around you.", Category.RENDER);
      this.biomeIds.defaultReturnValue(-1);
   }

   @Override
   public void onDisable() {
      this.clear();
   }

   @Override
   public void onWorldChange() {
      this.clear();
   }

   private void clear() {
      this.segmentCount = 0;
      this.biomeIds.clear();
      this.lastChunkX = Integer.MIN_VALUE;
      this.lastChunkZ = Integer.MIN_VALUE;
      this.lastBlockY = Integer.MIN_VALUE;
      this.lastRange = -1;
      this.countdown = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player == null || level == null) {
         return;
      }

      int chunkX = player.chunkPosition().x();
      int chunkZ = player.chunkPosition().z();
      int blockY = player.getBlockY();
      int radius = this.range.intValue();
      boolean moved = chunkX != this.lastChunkX || chunkZ != this.lastChunkZ || blockY != this.lastBlockY || radius != this.lastRange;
      if (!moved && --this.countdown > 0) {
         return;
      }

      this.countdown = REFRESH_TICKS;
      this.lastChunkX = chunkX;
      this.lastChunkZ = chunkZ;
      this.lastBlockY = blockY;
      this.lastRange = radius;
      this.sample(level, chunkX, chunkZ, blockY, radius);
   }

   private void sample(ClientLevel level, int chunkX, int chunkZ, int blockY, int radius) {
      int side = (radius * 2 + 1) * 4;
      if (this.ids.length != side * side) {
         this.ids = new int[side * side];
         // Every cell border could be a segment at worst: two per cell, four ints each.
         this.segments = new int[8 * side * side];
      }

      this.originX = (chunkX - radius) * 4;
      this.originZ = (chunkZ - radius) * 4;
      this.lineY = blockY;
      int quartY = blockY >> 2;
      for (int cz = 0; cz < radius * 2 + 1; cz++) {
         for (int cx = 0; cx < radius * 2 + 1; cx++) {
            boolean loaded = level.hasChunk(chunkX - radius + cx, chunkZ - radius + cz);
            for (int dz = 0; dz < 4; dz++) {
               for (int dx = 0; dx < 4; dx++) {
                  int i = cx * 4 + dx;
                  int j = cz * 4 + dz;
                  this.ids[j * side + i] = loaded ? this.idOf(level.getNoiseBiome(this.originX + i, quartY, this.originZ + j).value()) : -1;
               }
            }
         }
      }

      this.segmentCount = BiomeEdges.compute(this.ids, side, side, this.segments);
   }

   private int idOf(Biome biome) {
      int id = this.biomeIds.getInt(biome);
      if (id < 0) {
         id = this.biomeIds.size();
         this.biomeIds.put(biome, id);
      }

      return id;
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.segmentCount == 0) {
         return;
      }

      int argb = this.color.argb();
      float lineWidth = this.width.floatValue();
      boolean walls = this.throughWalls.get();
      double wall = this.height.get();
      double bottom = this.lineY + 0.02;
      double top = bottom + wall;
      for (int s = 0; s < this.segmentCount; s++) {
         int base = s * 4;
         double x1 = (this.originX + this.segments[base]) * 4.0;
         double z1 = (this.originZ + this.segments[base + 1]) * 4.0;
         double x2 = (this.originX + this.segments[base + 2]) * 4.0;
         double z2 = (this.originZ + this.segments[base + 3]) * 4.0;
         Render3D.lineArgb(x1, bottom, z1, x2, bottom, z2, argb, lineWidth, walls);
         if (wall > 0.0) {
            Render3D.lineArgb(x1, top, z1, x2, top, z2, argb, lineWidth, walls);
            Render3D.lineArgb(x1, bottom, z1, x1, top, z1, argb, lineWidth, walls);
            Render3D.lineArgb(x2, bottom, z2, x2, top, z2, argb, lineWidth, walls);
         }
      }
   }
}
