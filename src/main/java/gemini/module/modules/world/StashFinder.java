package gemini.module.modules.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.HudOverlay;
import gemini.util.ChunkScanner;
import gemini.util.InfoText;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds dense clusters of storage blocks in the loaded chunks. A {@link ChunkScanner} walks the chunks around you
 * a few columns per tick (and rescans only chunks that changed); once a second the hits are tallied per chunk, each
 * container type weighted, and every chunk whose cluster (itself plus its neighbours within the cluster radius)
 * reaches the threshold is reported once. Reported chunks are remembered per world (seed) and dimension, saved
 * with the config, so the same stash is never reported twice.
 *
 * <p>The log is a plain text file next to the Gemini config, appended on a background thread so the game never
 * waits for the disk.
 */
public final class StashFinder extends Module {
   private static final String[] TYPES = {"chest", "trapped_chest", "barrel", "shulker", "hopper", "dispenser"};
   private static final int TYPE_COUNT = TYPES.length;
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
   private static final ExecutorService LOG_IO = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Gemini-Stash-Log");
      thread.setDaemon(true);
      return thread;
   });
   private final BooleanSetting[] enabled = new BooleanSetting[TYPE_COUNT];
   private final NumberSetting[] weights = new NumberSetting[TYPE_COUNT];
   private final NumberSetting threshold;
   private final NumberSetting cluster;
   private final NumberSetting scanRadius;
   private final NumberSetting minSpawnDistance;
   private final BooleanSetting log;
   private final BooleanSetting waypoint;
   private final BooleanSetting toast;
   private final BooleanSetting render;
   private final BooleanSetting tracers;
   private final ColorSetting color;
   private final ChunkScanner scanner = new ChunkScanner(this::wanted, 6, 1_000_000).budgetMicros(800L);
   private final Long2ObjectOpenHashMap<Tally> tallies = new Long2ObjectOpenHashMap<>();
   private final ArrayList<Tally> tallyPool = new ArrayList<>();
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
   private final List<Stash> stashes = new ArrayList<>();
   private final Set<String> flagged = new HashSet<>();
   private int ticker;
   private final InfoText infoText = new InfoText();

   public StashFinder() {
      super("Stash Finder", "Scan loaded chunks for dense storage clusters, log and pin them. .stashfinder list | clear | rescan", Category.WORLD);
      String[] labels = {"Chests", "Trapped Chests", "Barrels", "Shulker Boxes", "Hoppers", "Dispensers & Droppers"};
      double[] defaults = {1.0, 1.0, 1.0, 3.0, 0.5, 0.5};
      for (int i = 0; i < TYPE_COUNT; i++) {
         BooleanSetting toggle = this.addSetting(new BooleanSetting(labels[i], "Count " + labels[i].toLowerCase(Locale.ROOT), i < 4));
         toggle.onChange(this.scanner::clear);
         this.enabled[i] = toggle;
         this.weights[i] = this.addSetting(
            new NumberSetting(labels[i] + " Weight", "How much one of these adds to a cluster's score", defaults[i], 0.5, 10.0, 0.5).showIf(toggle::get)
         );
      }

      this.threshold = this.addSetting(new NumberSetting("Threshold", "Score a cluster needs to count as a stash", 12.0, 2.0, 200.0, 1.0));
      this.cluster = this.addSetting(new NumberSetting("Cluster Radius", "Neighbouring chunks added to each chunk's score (0 = one chunk)", 1.0, 0.0, 3.0, 1.0));
      this.scanRadius = this.addSetting(new NumberSetting("Scan Radius", "Chunks around you that are scanned", 8.0, 2.0, 32.0, 1.0));
      this.minSpawnDistance = this.addSetting(
         new NumberSetting("Min Spawn Distance", "Ignore clusters closer than this to world spawn (Nether distances x8)", 500.0, 0.0, 20000.0, 50.0)
      );
      this.log = this.addSetting(new BooleanSetting("Log To File", "Append each find to gemini_stashes.log in the config folder", true));
      this.waypoint = this.addSetting(new BooleanSetting("Add Waypoint", "Pin each find in Waypoints", true));
      this.toast = this.addSetting(new BooleanSetting("Toast", "Pop up a notification for each find", true));
      this.render = this.addSetting(new BooleanSetting("Render", "Box found stashes in the world", true));
      this.tracers = this.addSetting(new BooleanSetting("Tracers", "Draw a line to each found stash", false).showIf(this.render::get));
      this.color = this.addSetting(new ColorSetting("Color", "Stash box colour", 45).showIf(this.render::get));
   }

   /** Per-chunk counts gathered from one pass over the scanner's hits. */
   private static final class Tally {
      final int[] counts = new int[TYPE_COUNT];
      long sumX;
      long sumY;
      long sumZ;
      int hits;
      int minX;
      int minY;
      int minZ;
      int maxX;
      int maxY;
      int maxZ;

      void reset() {
         java.util.Arrays.fill(this.counts, 0);
         this.sumX = this.sumY = this.sumZ = 0L;
         this.hits = 0;
         this.minX = this.minY = this.minZ = Integer.MAX_VALUE;
         this.maxX = this.maxY = this.maxZ = Integer.MIN_VALUE;
      }
   }

   public record Stash(String world, String dimension, int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int[] counts, double score, String found) {
      int total() {
         int sum = 0;
         for (int count : this.counts) {
            sum += count;
         }

         return sum;
      }

      String summary() {
         StringBuilder text = new StringBuilder();
         for (int i = 0; i < this.counts.length && i < TYPE_COUNT; i++) {
            if (this.counts[i] > 0) {
               if (text.length() > 0) {
                  text.append(' ');
               }

               text.append(TYPES[i]).append('=').append(this.counts[i]);
            }
         }

         return text.toString();
      }
   }

   private static int typeOf(Block block) {
      if (block == Blocks.CHEST) {
         return 0;
      } else if (block == Blocks.TRAPPED_CHEST) {
         return 1;
      } else if (block == Blocks.BARREL) {
         return 2;
      } else if (block instanceof ShulkerBoxBlock) {
         return 3;
      } else if (block == Blocks.HOPPER) {
         return 4;
      } else {
         return block != Blocks.DISPENSER && block != Blocks.DROPPER ? -1 : 5;
      }
   }

   private boolean wanted(BlockState state) {
      int type = typeOf(state.getBlock());
      return type >= 0 && this.enabled[type].get();
   }

   private static String worldId() {
      return WorldHelpers.worldKey();
   }

   private static String worldName() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      try {
         return server == null ? "?" : server.getWorldData().getLevelName();
      } catch (RuntimeException ignored) {
         return "?";
      }
   }

   private static String flagKey(String world, String dimension, int chunkX, int chunkZ) {
      return world + "|" + dimension + "|" + chunkX + "|" + chunkZ;
   }

   @Override
   public String info() {
      // Runs every frame: no string building unless the count changes.
      int count = 0;
      Minecraft client = Minecraft.getInstance();
      if (client.level != null) {
         String world = worldId();
         String dimension = WorldHelpers.dimensionName(client.level.dimension());
         for (Stash stash : this.stashes) {
            if (stash.world().equals(world) && stash.dimension().equals(dimension)) {
               count++;
            }
         }
      }

      return count == 0 ? null : this.infoText.whole(count, "");
   }

   @Override
   public void onDisable() {
      this.scanner.clear();
      this.recycleTallies();
   }

   @Override
   public void onWorldChange() {
      this.scanner.clear();
      this.recycleTallies();
   }

   private void recycleTallies() {
      this.tallyPool.addAll(this.tallies.values());
      this.tallies.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player == null || level == null) {
         return;
      }

      ChunkPos chunk = player.chunkPosition();
      this.scanner.tick(level, chunk.x(), chunk.z(), this.scanRadius.intValue());
      if (this.ticker++ % 20 == 0) {
         this.evaluate(level);
      }
   }

   private void evaluate(ClientLevel level) {
      this.recycleTallies();
      this.scanner.forEach((x, z) -> true, packed -> {
         int x = BlockPos.getX(packed);
         int y = BlockPos.getY(packed);
         int z = BlockPos.getZ(packed);
         int type = typeOf(level.getBlockState(this.cursor.set(x, y, z)).getBlock());
         if (type < 0 || !this.enabled[type].get()) {
            return;
         }

         long key = ChunkPos.pack(x >> 4, z >> 4);
         Tally tally = this.tallies.get(key);
         if (tally == null) {
            tally = this.tallyPool.isEmpty() ? new Tally() : this.tallyPool.remove(this.tallyPool.size() - 1);
            tally.reset();
            this.tallies.put(key, tally);
         }

         tally.counts[type]++;
         tally.sumX += x;
         tally.sumY += y;
         tally.sumZ += z;
         tally.hits++;
         tally.minX = Math.min(tally.minX, x);
         tally.minY = Math.min(tally.minY, y);
         tally.minZ = Math.min(tally.minZ, z);
         tally.maxX = Math.max(tally.maxX, x);
         tally.maxY = Math.max(tally.maxY, y);
         tally.maxZ = Math.max(tally.maxZ, z);
      });
      if (this.tallies.isEmpty()) {
         return;
      }

      String world = worldId();
      String dimension = level.dimension().identifier().toString();
      int radius = this.cluster.intValue();
      List<long[]> candidates = new ArrayList<>();
      for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<Tally> entry : this.tallies.long2ObjectEntrySet()) {
         long key = entry.getLongKey();
         double score = this.clusterScore(ChunkPos.getX(key), ChunkPos.getZ(key), radius);
         if (score >= this.threshold.get()) {
            candidates.add(new long[]{key, Double.doubleToLongBits(score)});
         }
      }

      candidates.sort((a, b) -> Double.compare(Double.longBitsToDouble(b[1]), Double.longBitsToDouble(a[1])));
      for (long[] candidate : candidates) {
         int cx = ChunkPos.getX(candidate[0]);
         int cz = ChunkPos.getZ(candidate[0]);
         if (!this.anyFlagged(world, dimension, cx, cz, radius)) {
            this.report(level, world, dimension, cx, cz, radius, Double.longBitsToDouble(candidate[1]));
         }
      }
   }

   private double clusterScore(int cx, int cz, int radius) {
      double score = 0.0;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            Tally tally = this.tallies.get(ChunkPos.pack(cx + dx, cz + dz));
            if (tally != null) {
               for (int i = 0; i < TYPE_COUNT; i++) {
                  score += tally.counts[i] * this.weights[i].get();
               }
            }
         }
      }

      return score;
   }

   private boolean anyFlagged(String world, String dimension, int cx, int cz, int radius) {
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (this.flagged.contains(flagKey(world, dimension, cx + dx, cz + dz))) {
               return true;
            }
         }
      }

      return false;
   }

   private void markFlagged(Stash stash, int radius) {
      int cx = stash.x() >> 4;
      int cz = stash.z() >> 4;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            this.flagged.add(flagKey(stash.world(), stash.dimension(), cx + dx, cz + dz));
         }
      }
   }

   private void report(ClientLevel level, String world, String dimension, int cx, int cz, int radius, double score) {
      int[] counts = new int[TYPE_COUNT];
      long sumX = 0L;
      long sumY = 0L;
      long sumZ = 0L;
      int hits = 0;
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            Tally tally = this.tallies.get(ChunkPos.pack(cx + dx, cz + dz));
            if (tally != null) {
               for (int i = 0; i < TYPE_COUNT; i++) {
                  counts[i] += tally.counts[i];
               }

               sumX += tally.sumX;
               sumY += tally.sumY;
               sumZ += tally.sumZ;
               hits += tally.hits;
               minX = Math.min(minX, tally.minX);
               minY = Math.min(minY, tally.minY);
               minZ = Math.min(minZ, tally.minZ);
               maxX = Math.max(maxX, tally.maxX);
               maxY = Math.max(maxY, tally.maxY);
               maxZ = Math.max(maxZ, tally.maxZ);
            }
         }
      }

      if (hits == 0) {
         return;
      }

      int x = (int)Math.floorDiv(sumX, (long)hits);
      int y = (int)Math.floorDiv(sumY, (long)hits);
      int z = (int)Math.floorDiv(sumZ, (long)hits);
      if (this.tooCloseToSpawn(level, x, z)) {
         return;
      }

      String found = LocalDateTime.now().format(TIME);
      Stash stash = new Stash(world, dimension, x, y, z, minX, minY, minZ, maxX, maxY, maxZ, counts, score, found);
      this.stashes.add(stash);
      this.markFlagged(stash, radius);
      this.save();
      if (this.log.get()) {
         this.appendLog(stash);
      }

      if (this.waypoint.get() && Gemini.getInstance() != null && Gemini.getInstance().modules() != null) {
         String name = "Stash " + this.stashes.size();
         Gemini.getInstance().modules().byClass(WaypointsModule.class).ifPresent(waypoints -> waypoints.pin(name, x, y, z));
      }

      if (this.toast.get()) {
         HudOverlay.toast("Stash found", x + " " + y + " " + z + "  " + stash.total() + " containers", HudOverlay.Severity.SUCCESS);
      }

      CommandManager.feedback("§bStash Finder §7found §f" + stash.summary() + " §7at §f" + x + " " + y + " " + z);
   }

   private boolean tooCloseToSpawn(ClientLevel level, int x, int z) {
      double min = this.minSpawnDistance.get();
      if (min <= 0.0) {
         return false;
      }

      BlockPos spawn = level.getRespawnData().pos();
      double scale = level.dimension().identifier().getPath().equals("the_nether") ? 8.0 : 1.0;
      double dx = x * scale - spawn.getX();
      double dz = z * scale - spawn.getZ();
      return dx * dx + dz * dz < min * min;
   }

   private void appendLog(Stash stash) {
      String line = String.format(
         Locale.ROOT,
         "%s | world %s (%s) | %s | %d %d %d | %s | score %.1f%n",
         stash.found(),
         worldName(),
         stash.world(),
         stash.dimension(),
         stash.x(),
         stash.y(),
         stash.z(),
         stash.summary(),
         stash.score()
      );
      Path file = FabricLoader.getInstance().getConfigDir().resolve("gemini_stashes.log");
      LOG_IO.execute(() -> {
         try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (IOException e) {
            Gemini.LOGGER.warn("Stash Finder could not write {}: {}", file, e.toString());
         }
      });
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (!this.render.get() || client.level == null || client.player == null || this.stashes.isEmpty()) {
         return;
      }

      String world = worldId();
      String dimension = WorldHelpers.dimensionName(client.level.dimension());
      int argb = this.color.argb();
      Vec3 crosshair = this.tracers.get() ? Render3D.crosshair() : null;
      for (Stash stash : this.stashes) {
         if (!stash.world().equals(world) || !stash.dimension().equals(dimension)) {
            continue;
         }

         Render3D.box(stash.minX(), stash.minY(), stash.minZ(), stash.maxX() + 1.0, stash.maxY() + 1.0, stash.maxZ() + 1.0, argb, true, true, 2.0F, 30, true);
         if (crosshair != null) {
            Render3D.line(crosshair.x, crosshair.y, crosshair.z, stash.x() + 0.5, stash.y() + 0.5, stash.z() + 0.5, argb, 1.5F, true);
         }
      }
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         return false;
      }

      String world = worldId();
      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "list", "ls" -> {
            int shown = 0;
            for (Stash stash : this.stashes) {
               if (stash.world().equals(world)) {
                  shown++;
                  CommandManager.feedback(
                     "§f" + shown + ". §b" + stash.x() + " " + stash.y() + " " + stash.z() + " §8(" + stash.dimension().replace("minecraft:", "") + ") §7" + stash.summary()
                  );
               }
            }

            if (shown == 0) {
               CommandManager.feedback("§7No stashes found in this world yet.");
            }
         }
         case "clear" -> {
            int before = this.stashes.size();
            this.stashes.removeIf(stash -> stash.world().equals(world));
            this.flagged.removeIf(key -> key.startsWith(world + "|"));
            this.save();
            CommandManager.feedback("§bStash Finder §7forgot " + (before - this.stashes.size()) + " stashes in this world");
         }
         case "rescan" -> {
            this.scanner.clear();
            CommandManager.feedback("§bStash Finder §7rescanning loaded chunks");
         }
         default -> {
            return false;
         }
      }

      return true;
   }

   @Override
   public void writeExtra(JsonObject object) {
      JsonArray array = new JsonArray();
      for (Stash stash : this.stashes) {
         JsonObject entry = new JsonObject();
         entry.addProperty("world", stash.world());
         entry.addProperty("dimension", stash.dimension());
         entry.addProperty("x", stash.x());
         entry.addProperty("y", stash.y());
         entry.addProperty("z", stash.z());
         entry.addProperty("minX", stash.minX());
         entry.addProperty("minY", stash.minY());
         entry.addProperty("minZ", stash.minZ());
         entry.addProperty("maxX", stash.maxX());
         entry.addProperty("maxY", stash.maxY());
         entry.addProperty("maxZ", stash.maxZ());
         JsonArray counts = new JsonArray();
         for (int count : stash.counts()) {
            counts.add(count);
         }

         entry.add("counts", counts);
         entry.addProperty("score", stash.score());
         entry.addProperty("found", stash.found());
         array.add(entry);
      }

      object.add("stashes", array);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.stashes.clear();
      this.flagged.clear();
      if (object.has("stashes") && object.get("stashes").isJsonArray()) {
         for (JsonElement element : object.getAsJsonArray("stashes")) {
            try {
               JsonObject entry = element.getAsJsonObject();
               int[] counts = new int[TYPE_COUNT];
               JsonArray saved = entry.getAsJsonArray("counts");
               for (int i = 0; i < counts.length && i < saved.size(); i++) {
                  counts[i] = saved.get(i).getAsInt();
               }

               Stash stash = new Stash(
                  entry.get("world").getAsString(),
                  entry.get("dimension").getAsString(),
                  entry.get("x").getAsInt(),
                  entry.get("y").getAsInt(),
                  entry.get("z").getAsInt(),
                  entry.get("minX").getAsInt(),
                  entry.get("minY").getAsInt(),
                  entry.get("minZ").getAsInt(),
                  entry.get("maxX").getAsInt(),
                  entry.get("maxY").getAsInt(),
                  entry.get("maxZ").getAsInt(),
                  counts,
                  entry.get("score").getAsDouble(),
                  entry.get("found").getAsString()
               );
               this.stashes.add(stash);
               this.markFlagged(stash, this.cluster.intValue());
            } catch (RuntimeException ignored) {
               // One damaged entry should not lose the rest.
            }
         }
      }
   }
}
