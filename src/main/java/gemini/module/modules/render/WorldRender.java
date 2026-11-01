package gemini.module.modules.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import gemini.util.ChunkScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldRender {
   private WorldRender() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new WorldRender.LightOverlay());
      sink.accept(new WorldRender.Search());
      sink.accept(new WorldRender.ChunkBorders());
      sink.accept(new WorldRender.BlockSelection());
      sink.accept(new WorldRender.Marker());
   }

   public static final class BlockSelection extends Module {
      private final ModeSetting colors = this.addSetting(new ModeSetting("Colors", "Fixed color or an animated rainbow", "Custom", "Custom", "Rainbow"));
      private final ColorSetting custom = this.addSetting(new ColorSetting("Color", "Outline color", 175).showIf(() -> this.colors.is("Custom")));
      private final BooleanSetting exactShape = this.addSetting(
         new BooleanSetting("Exact Shape", "Follow stairs, slabs and fences instead of a plain box", true)
      );
      private final ShapeSettings shape = new ShapeSettings(this, "Both", false);

      public BlockSelection() {
         super("Block Selection", "Recolor the outline of the block you are looking at.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && client.level != null && client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            if (!state.isAir()) {
               int color = this.colors.is("Rainbow")
                  ? ColorSetting.hueColor((float)(System.currentTimeMillis() % 4000L) / 4000.0F, 0.7F, 1.0F)
                  : this.custom.argb();
               if (!this.exactShape.get()) {
                  this.shape.block(pos, color);
               } else {
                  VoxelShape voxel = state.getShape(client.level, pos, CollisionContext.of(client.player));
                  if (voxel.isEmpty()) {
                     this.shape.block(pos, color);
                  } else {
                     for (AABB box : voxel.toAabbs()) {
                        this.shape.box(box.move(pos).inflate(0.002), color);
                     }
                  }
               }
            }
         }
      }
   }

   public static final class ChunkBorders extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Extra chunks drawn around yours", 0.0, 0.0, 3.0, 1.0));
      private final NumberSetting vertical = this.addSetting(
         new NumberSetting("Vertical Range", "Blocks above and below you that get lines", 48.0, 16.0, 256.0, 16.0)
      );
      private final BooleanSetting sections = this.addSetting(new BooleanSetting("Section Lines", "Rings every 16 blocks of height", true));
      private final ColorSetting edge = this.addSetting(new ColorSetting("Chunk Color", "Color of your chunk's edges", 210));
      private final ColorSetting inner = this.addSetting(new ColorSetting("Section Color", "Color of the 16-block rings", 120));
      private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the lines", 1.5, 0.5, 4.0, 0.5));

      public ChunkBorders() {
         super("Chunk Borders", "Draw chunk and section boundaries around you.", Category.RENDER);
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         if (player != null && level != null) {
            int cx = player.chunkPosition().x();
            int cz = player.chunkPosition().z();
            int extra = this.radius.intValue();
            int py = Mth.floor(player.getY());
            int top = Math.min(level.getMaxY() + 1, py + this.vertical.intValue());
            int bottom = Math.max(level.getMinY(), py - this.vertical.intValue());
            float stroke = this.width.floatValue();
            int edgeColor = this.edge.argb();
            int innerColor = this.inner.argb();
            int west = (cx - extra) * 16;
            int east = (cx + extra + 1) * 16;
            int north = (cz - extra) * 16;
            int south = (cz + extra + 1) * 16;

            for (int x = cx - extra; x <= cx + extra + 1; x++) {
               double wx = x * 16.0;
               boolean own = x == cx || x == cx + 1;

               for (int z = cz - extra; z <= cz + extra + 1; z++) {
                  double wz = z * 16.0;
                  boolean corner = own && (z == cz || z == cz + 1);
                  Render3D.line(new Vec3(wx, bottom, wz), new Vec3(wx, top, wz), corner ? edgeColor : innerColor, corner ? stroke : stroke * 0.6F, false);
               }
            }

            int firstRing = Math.floorDiv(bottom, 16) * 16;

            for (int y = firstRing; y <= top; y += 16) {
               if (y >= bottom && (this.sections.get() || Math.abs(y - py) <= 16)) {
                  ring(west, east, north, south, y, innerColor, stroke * 0.6F);
               }
            }

            ring(cx * 16, (cx + 1) * 16, cz * 16, (cz + 1) * 16, py, edgeColor, stroke);
         }
      }

      private static void ring(double west, double east, double north, double south, double y, int color, float width) {
         Vec3 a = new Vec3(west, y, north);
         Vec3 b = new Vec3(east, y, north);
         Vec3 c = new Vec3(east, y, south);
         Vec3 d = new Vec3(west, y, south);
         Render3D.line(a, b, color, width, false);
         Render3D.line(b, c, color, width, false);
         Render3D.line(c, d, color, width, false);
         Render3D.line(d, a, color, width, false);
      }
   }

   public static final class LightOverlay extends Module {
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
   }

   public static final class Marker extends Module {
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Marker color", 280));
      private final BooleanSetting labels = this.addSetting(new BooleanSetting("Labels", "Number each marker", true));
      private final ShapeSettings shape = new ShapeSettings(this, "Both", true);
      private final List<AABB> boxes = new ArrayList<>();

      public Marker() {
         super("Marker", "Draw boxes you place yourself. Use .marker add / box / remove / clear / list.", Category.RENDER);
         this.addSetting(new ActionSetting("Mark Here", "Add a marker on your block", this::markHere));
         this.addSetting(new ActionSetting("Clear", "Remove every marker", this::clear));
      }

      private void markHere() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            BlockPos pos = player.blockPosition();
            this.boxes.add(new AABB(pos));
            CommandManager.feedback("§bMarker §7added #" + this.boxes.size() + " at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            if (!this.isSavedEnabled()) {
               this.setEnabled(true);
            } else {
               this.save();
            }
         }
      }

      private void clear() {
         this.boxes.clear();
         CommandManager.feedback("§bMarker §7cleared");
         this.save();
      }

      @Override
      public boolean onCommand(String[] args) {
         if (args.length == 0) {
            return false;
         } else {
            String var2 = args[0].toLowerCase(Locale.ROOT);
            switch (var2) {
               case "add":
                  if (args.length >= 4) {
                     Integer x = parse(args[1]);
                     Integer y = parse(args[2]);
                     Integer z = parse(args[3]);
                     if (x == null || y == null || z == null) {
                        return false;
                     }

                     this.boxes.add(new AABB(new BlockPos(x, y, z)));
                     CommandManager.feedback("§bMarker §7added #" + this.boxes.size());
                     this.save();
                  } else {
                     this.markHere();
                  }
                  break;
               case "box":
                  if (args.length < 7) {
                     return false;
                  }

                  double[] v = new double[6];

                  for (int i = 0; i < 6; i++) {
                     try {
                        v[i] = Double.parseDouble(args[i + 1]);
                     } catch (NumberFormatException var9) {
                        return false;
                     }
                  }

                  this.boxes.add(new AABB(v[0], v[1], v[2], v[3] + 1.0, v[4] + 1.0, v[5] + 1.0));
                  CommandManager.feedback("§bMarker §7added #" + this.boxes.size());
                  this.save();
                  break;
               case "remove":
                  Integer index = args.length > 1 ? parse(args[1]) : null;
                  if (index == null || index < 1 || index > this.boxes.size()) {
                     CommandManager.feedback("§cNo marker with that number. Use §f.marker list");
                     return true;
                  }

                  this.boxes.remove(index - 1);
                  this.save();
                  CommandManager.feedback("§bMarker §7removed #" + index);
                  break;
               case "clear":
                  this.clear();
                  break;
               case "list":
                  if (this.boxes.isEmpty()) {
                     CommandManager.feedback("§7No markers yet.");
                  }

                  for (int i = 0; i < this.boxes.size(); i++) {
                     AABB box = this.boxes.get(i);
                     CommandManager.feedback(
                        "§7#"
                           + (i + 1)
                           + " §f"
                           + (int)box.minX
                           + " "
                           + (int)box.minY
                           + " "
                           + (int)box.minZ
                           + " §7to §f"
                           + (int)(box.maxX - 1.0)
                           + " "
                           + (int)(box.maxY - 1.0)
                           + " "
                           + (int)(box.maxZ - 1.0)
                     );
                  }
                  break;
               default:
                  return false;
            }

            return true;
         }
      }

      private static Integer parse(String text) {
         try {
            return Integer.parseInt(text);
         } catch (NumberFormatException var2) {
            return null;
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         int argb = this.color.argb();

         for (int i = 0; i < this.boxes.size(); i++) {
            AABB box = this.boxes.get(i);
            this.shape.box(box, argb);
            if (this.labels.get()) {
               Render3D.text("#" + (i + 1), box.getCenter().add(0.0, box.getYsize() / 2.0 + 0.4, 0.0), argb, 0.4F, true);
            }
         }
      }

      @Override
      public void writeExtra(JsonObject object) {
         JsonArray array = new JsonArray();

         for (AABB box : this.boxes) {
            JsonArray entry = new JsonArray();
            entry.add(box.minX);
            entry.add(box.minY);
            entry.add(box.minZ);
            entry.add(box.maxX);
            entry.add(box.maxY);
            entry.add(box.maxZ);
            array.add(entry);
         }

         object.add("markers", array);
      }

      @Override
      public void readExtra(JsonObject object) {
         this.boxes.clear();
         if (object.has("markers") && object.get("markers").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("markers")) {
               JsonArray entry = element.getAsJsonArray();
               if (entry.size() == 6) {
                  this.boxes
                     .add(
                        new AABB(
                           entry.get(0).getAsDouble(),
                           entry.get(1).getAsDouble(),
                           entry.get(2).getAsDouble(),
                           entry.get(3).getAsDouble(),
                           entry.get(4).getAsDouble(),
                           entry.get(5).getAsDouble()
                        )
                     );
               }
            }
         }
      }
   }

   public static final class Search extends Module {
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
         super("Search", "Highlight chosen blocks anywhere in the loaded chunks. Edit the list with the GUI or .search add <block>.", Category.RENDER);
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
            int bottom = level.getMinY();
            int top = level.getMaxY() + 1;
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
}
