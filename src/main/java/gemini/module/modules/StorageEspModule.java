package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class StorageEspModule extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Scan distance in blocks", 64.0, 8.0, 160.0, 4.0));
   private final ModeSetting style = this.addSetting(new ModeSetting("Style", "3D box rendering style", "Box + Fill", "Box + Fill", "Outline", "Glow", "Fill"));
   private final BooleanSetting boxes3D = this.addSetting(new BooleanSetting("3D Boxes", "Highlight storage blocks in 3D through walls", true));
   private final BooleanSetting tracers = this.addSetting(new BooleanSetting("Tracers", "Draw tracer lines to storage blocks", false));
   private final BooleanSetting labels3D = this.addSetting(new BooleanSetting("3D Labels", "Draw floating labels above storage blocks", false));
   private final BooleanSetting hudList = this.addSetting(new BooleanSetting("HUD List", "Also list nearby storage blocks on the HUD", true));
   private final NumberSetting maxResults = this.addSetting(
      new NumberSetting("Max HUD Entries", "Most entries in the HUD list", 10.0, 3.0, 25.0, 1.0).showIf(this.hudList::get)
   );
   private final BooleanSetting chests = this.addSetting(new BooleanSetting("Chests", "Highlight chests and trapped chests", true));
   private final BooleanSetting barrels = this.addSetting(new BooleanSetting("Barrels", "Highlight barrels", true));
   private final BooleanSetting shulkers = this.addSetting(new BooleanSetting("Shulkers", "Highlight shulker boxes", true));
   private final BooleanSetting ender = this.addSetting(new BooleanSetting("Ender Chests", "Highlight ender chests", true));
   private final BooleanSetting furnaces = this.addSetting(
      new BooleanSetting("Furnaces & Hoppers", "Highlight furnaces, smokers, blast furnaces, and hoppers", false)
   );
   private final BooleanSetting spawners = this.addSetting(new BooleanSetting("Spawners", "Highlight mob spawners", true));
   private final List<StorageEspModule.Hit> allHits = new ArrayList<>();
   private final List<StorageEspModule.Hit> hits = new ArrayList<>();
   private int ticker;

   public StorageEspModule() {
      super("StorageESP", "Highlight chests, shulkers, ender chests, barrels.", Category.RENDER);
   }

   public List<StorageEspModule.Hit> hits() {
      return this.hudList.get() ? this.hits : List.of();
   }

   @Override
   public String info() {
      return this.allHits.isEmpty() ? null : Integer.toString(this.allHits.size());
   }

   @Override
   public void onDisable() {
      this.allHits.clear();
      this.hits.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null && this.ticker++ % 10 == 0) {
         BlockPos origin = client.player.blockPosition();
         double maxDistSq = this.range.get() * this.range.get();
         int chunkRadius = (int)Math.ceil(this.range.get() / 16.0);
         int centerX = origin.getX() >> 4;
         int centerZ = origin.getZ() >> 4;
         List<StorageEspModule.Hit> found = new ArrayList<>();

         for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
               LevelChunk chunk = client.level.getChunkSource().getChunkNow(centerX + x, centerZ + z);
               if (chunk != null) {
                  for (BlockEntity entity : chunk.getBlockEntities().values()) {
                     if (!(entity.getBlockPos().distSqr(origin) > maxDistSq)) {
                        String label = this.label(entity);
                        if (label != null) {
                           found.add(new StorageEspModule.Hit(entity.getBlockPos().immutable(), label));
                        }
                     }
                  }
               }
            }
         }

         found.sort(Comparator.comparingDouble(hit -> hit.pos().distSqr(origin)));
         this.allHits.clear();
         this.allHits.addAll(found.subList(0, Math.min(found.size(), 180)));
         this.hits.clear();
         this.hits.addAll(found.subList(0, Math.min(found.size(), this.maxResults.intValue())));
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.boxes3D.get() || this.tracers.get() || this.labels3D.get()) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null && !this.allHits.isEmpty()) {
            Vec3 crosshair = Render3D.crosshair();
            String mode = this.style.get();
            boolean outline = !mode.equals("Fill");
            boolean fill = mode.equals("Box + Fill") || mode.equals("Fill") || mode.equals("Glow");

            for (StorageEspModule.Hit hit : this.allHits) {
               BlockPos pos = hit.pos();
               int color = colorFor(hit.label());
               if (this.boxes3D.get() && Render3D.visibleBlock(pos.getX(), pos.getY(), pos.getZ())) {
                  if (mode.equals("Glow")) {
                     Render3D.boxGlow(new AABB(pos), color, 2.0F, 65, true);
                  } else {
                     Render3D.block(pos, color, outline, fill, 1.6F, 50, true);
                  }
               }

               if (this.tracers.get()) {
                  Render3D.line(crosshair, Vec3.atCenterOf(pos), color, 1.2F, true);
               }

               if (this.labels3D.get()) {
                  int dist = (int)Math.sqrt(pos.distSqr(client.player.blockPosition()));
                  Render3D.text(hit.label() + " " + dist + "m", Vec3.atCenterOf(pos).add(0.0, 0.75, 0.0), color, 0.75F, true);
               }
            }
         }
      }
   }

   private static int colorFor(String label) {
      return switch (label) {
         case "Chest" -> -21982;
         case "Ender" -> -4504321;
         case "Shulker" -> -43572;
         case "Barrel" -> -2586056;
         case "Spawner" -> -52412;
         default -> -11154177;
      };
   }

   private String label(BlockEntity entity) {
      if (this.chests.get() && entity instanceof ChestBlockEntity) {
         return "Chest";
      } else if (!this.barrels.get() || !(entity instanceof BarrelBlockEntity) && !entity.getBlockState().is(Blocks.BARREL)) {
         if (this.shulkers.get() && entity instanceof ShulkerBoxBlockEntity) {
            return "Shulker";
         } else if (this.ender.get() && entity instanceof EnderChestBlockEntity) {
            return "Ender";
         } else if (this.spawners.get() && entity instanceof SpawnerBlockEntity) {
            return "Spawner";
         } else {
            return !this.furnaces.get() || !(entity instanceof AbstractFurnaceBlockEntity) && !(entity instanceof HopperBlockEntity) ? null : "Utility";
         }
      } else {
         return "Barrel";
      }
   }

   public record Hit(BlockPos pos, String label) {
   }
}
