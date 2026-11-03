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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SlimeChunks extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Chunks around you to check", 6.0, 1.0, 12.0, 1.0));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 120));
   private long seed;
   private boolean haveSeed;

   public SlimeChunks() {
      super("Slime Chunks", "Outline overworld chunks where slimes can spawn.", Category.RENDER);
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

         for (int x = cx - extra; x <= cx + extra; x++) {
            for (int z = cz - extra; z <= cz + extra; z++) {
               if (isSlimeChunk(this.seed, x, z)) {
                  BlockPos mark = new BlockPos(x * 16 + 8, floor, z * 16 + 8);
                  Render3D.floorMark(mark, 0.05, argb, true, true, 1.5F, 50, false);
                  // Slimes spawn below y = 40 in slime chunks: outline that band, starting a little below you.
                  int top = 40;
                  int bottom = Math.max(VersionBridge.minWorldY(client.level), Math.min(floor - 2, top - 1));
                  Render3D.box(
                     new AABB(x * 16, bottom, z * 16, x * 16 + 16, top, z * 16 + 16),
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
