package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.VersionBridge;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SlimeChunks extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "How far around you to check, in chunks", 6.0, 1.0, 12.0, 1.0));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Outline colour", 120));
   private long seed;
   private boolean haveSeed;
   private final MutableBlockPos mark = new MutableBlockPos();
   private boolean[] grid = new boolean[0];
   private int gridX = Integer.MIN_VALUE;
   private int gridZ = Integer.MIN_VALUE;
   private int gridRadius = -1;
   private long gridSeed;

   public SlimeChunks() {
      super("Slime Chunks", "Outlines Overworld chunks where slimes can spawn.", Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.haveSeed = false;
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server != null) {
         this.seed = server.overworld().getSeed();
         this.haveSeed = true;
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (this.haveSeed && player != null && client.level != null && client.level.dimension() == Level.OVERWORLD) {
         int cx = player.chunkPosition().x();
         int cz = player.chunkPosition().z();
         int extra = this.radius.intValue();
         int floor = Mth.floor(player.getY());
         int argb = this.color.argb();

         int side = extra * 2 + 1;
         if (cx != this.gridX || cz != this.gridZ || extra != this.gridRadius || this.seed != this.gridSeed) {
            // Which chunks are slime chunks only changes when you cross into another chunk.
            this.gridX = cx;
            this.gridZ = cz;
            this.gridRadius = extra;
            this.gridSeed = this.seed;
            if (this.grid.length != side * side) {
               this.grid = new boolean[side * side];
            }

            for (int i = 0; i < side; i++) {
               for (int j = 0; j < side; j++) {
                  this.grid[i * side + j] = isSlimeChunk(this.seed, cx - extra + i, cz - extra + j);
               }
            }
         }

         for (int x = cx - extra; x <= cx + extra; x++) {
            for (int z = cz - extra; z <= cz + extra; z++) {
               if (this.grid[(x - cx + extra) * side + (z - cz + extra)]) {
                  BlockPos mark = this.mark.set(x * 16 + 8, floor, z * 16 + 8);
                  Render3D.floorMark(mark, 0.05, argb, true, true, 1.5F, 50, false);
                  // Slimes spawn below y = 40 in slime chunks: outline that band, starting a little below you.
                  int top = 40;
                  int bottom = Math.max(VersionBridge.minWorldY(client.level), Math.min(floor - 2, top - 1));
                  Render3D.box(
                     x * 16.0, bottom, z * 16.0, x * 16.0 + 16.0, top, z * 16.0 + 16.0,
                     argb,
                     true,
                     false,
                     1.0F,
                     0,
                     false
                  );
               }
            }
         }
      }
   }

   static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
      Random random = new Random(worldSeed + chunkX * chunkX * 4987142 + chunkX * 5947611 + chunkZ * chunkZ * 4392871L + chunkZ * 389711 ^ 987234911L);
      return random.nextInt(10) == 0;
   }

   @Override
   public String info() {
      return this.haveSeed ? "seed" : "no seed";
   }
}
