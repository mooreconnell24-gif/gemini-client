package gemini.util;

import gemini.command.CommandManager;
import gemini.event.ActionPriority;
import gemini.render.Render3D;
import gemini.util.pathing.BlockView;
import gemini.util.pathing.PathSearch;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The fallback pathfinder used when the real Baritone mod is not installed (see {@link BaritoneBridge}).
 *
 * <p>It is a 3D A* over block positions with walking, stepping, dropping, swimming, climbing, pillaring, bridging,
 * mining through walls and one- or two-block parkour. Move costs are in "blocks walked": breaking a block costs
 * what the best hotbar tool needs (so a stone wall is cheap with a pickaxe and avoided with bare hands), sand or
 * gravel above a block to break adds the time it takes to clear the pour, standing next to lava is penalised and
 * lava, fire and magma are never entered.
 *
 * <p><b>Search.</b> The search itself is {@link PathSearch}: nodes in reused primitive arrays, an indexed heap and
 * an octile heuristic, so a search allocates nothing once warm. Blocks are read through a {@link BlockView} that
 * caches chunks and per-state flags. Each search runs for at most a few milliseconds on the client thread; when it
 * runs out it returns the best partial path and continues from there on the next repath.
 *
 * <p><b>Staying on a path.</b> A path is kept while the blocks ahead are still walkable instead of being thrown
 * away on a timer. Spots the player got stuck at are avoided for a while so a failed jump or a mob in the way does
 * not loop forever. Three situations that used to look like "stuck" are handled on their own: a pause (combat, an
 * open screen) drops the path and starts fresh on resume, a long tick is given a grace period before the stuck
 * counter runs again, and a search that ran into unloaded chunks waits for them rather than counting as a failure.
 *
 * <p>Block searches for the mine and farm tasks use the incremental {@link ChunkScanner} instead of rescanning a
 * cube around the player every tick.
 */
public final class BuiltInBaritone {
   private static final int MAX_A_STAR_ITERATIONS = 9000;
   private static final long PATH_BUDGET_NANOS = 4_500_000L;
   private static final int[] VERTICAL_STEPS = new int[]{1, -1};
   private static final int[][] OFFSETS_8 = new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {1, -1}, {1, 1}, {-1, -1}, {-1, 1}};
   private static final int[][] CARDINAL_4 = new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
   private static final double AVOID_PENALTY = 8.0;
   private static final int AVOID_TICKS = 240;
   private static final int TARGET_GIVE_UP_TICKS = 600;
   private static final int TARGET_AVOID_TICKS = 1200;
   /** Remaining path nodes checked each time the path comes up for review. */
   private static final int PATH_LOOKAHEAD = 8;
   /** A path is reviewed this often; it is only searched again when the review fails. */
   private static final int REVIEW_TICKS = 32;
   /** Even a path that keeps passing review is searched again after this long. */
   private static final int MAX_PATH_AGE_TICKS = 160;
   /** A tick that took longer than this is a lag spike, not the player being stuck. */
   private static final long LAG_SPIKE_NANOS = 200_000_000L;
   private static final int LAG_GRACE_TICKS = 10;
   private static final int CHUNK_WAIT_TICKS = 10;
   private static final double NO_COST = Double.NaN;
   private static final MutableBlockPos SCRATCH = new MutableBlockPos();
   private static final Long2LongOpenHashMap AVOID = new Long2LongOpenHashMap();
   private static final Long2LongOpenHashMap AVOID_TARGETS = new Long2LongOpenHashMap();
   private static final Reference2DoubleOpenHashMap<BlockState> BREAK_COSTS = new Reference2DoubleOpenHashMap<>();
   private static final BlockView VIEW = new BlockView();
   private static final PathSearch SEARCH = new PathSearch().tune(1.1, 0.65, 0.3);
   private static final PathSearch.MoveGenerator MOVES = BuiltInBaritone::expand;
   private static boolean searchCanBreak;
   /** The last search ended at the goal (false: a partial path toward it). */
   private static boolean pathComplete;
   /** The last search ran into unloaded chunks. */
   private static boolean pathHitUnloaded;
   private static BlockPos pathGoal;
   private static int pathAge;
   private static int chunkWait;
   private static int lagGrace;
   private static long lastTickNanos;
   private static boolean held;
   private static int heldSince;
   private static int lastSearchNodes;
   private static long lastSearchNanos;
   private static BuiltInBaritone.TaskKind activeTask = BuiltInBaritone.TaskKind.NONE;
   private static BlockPos goalPos;
   private static BlockPos selPos1;
   private static BlockPos selPos2;
   private static final List<BlockPos> currentPath = new ArrayList<>();
   private static int pathIndex;
   private static int repathTimer;
   private static int stuckTicks;
   private static int clock;
   private static int targetSince;
   private static int failedPaths;
   private static int lastMineClock = Integer.MIN_VALUE / 2;
   private static Vec3 lastPlayerPos = Vec3.ZERO;
   private static LocalPlayer costPlayer;
   private static final Set<Block> mineTargets = new HashSet<>();
   private static ChunkScanner mineScanner;
   private static ChunkScanner farmScanner;
   private static BlockPos pickBest;
   private static double pickBestScore;
   private static Predicate<Entity> followFilter;
   private static Direction tunnelDirection = Direction.NORTH;
   private static int tunnelRemaining;
   private static BlockPos excavateMin;
   private static BlockPos excavateMax;
   private static int excavateX;
   private static int excavateY;
   private static int excavateZ;
   private static boolean excavateFoundInPass;
   private static int excavateSkippedInPass;
   private static BlockPos exploreOrigin;
   private static int exploreStep;
   private static boolean allowBreak = true;
   private static boolean allowPlace = true;
   private static boolean allowSprint = true;
   private static boolean allowParkour = true;
   private static boolean renderPath = true;
   private static int pathColor = -16722689;
   private static int goalColor = -12161025;

   static {
      AVOID.defaultReturnValue(Long.MIN_VALUE);
      AVOID_TARGETS.defaultReturnValue(Long.MIN_VALUE);
      BREAK_COSTS.defaultReturnValue(NO_COST);
   }

   private BuiltInBaritone() {
   }

   public static boolean isActive() {
      return activeTask != BuiltInBaritone.TaskKind.NONE;
   }

   public static BuiltInBaritone.TaskKind task() {
      return activeTask;
   }

   public static BlockPos goal() {
      return goalPos;
   }

   public static void setGoalOnly(BlockPos pos) {
      goalPos = pos == null ? null : pos.immutable();
   }

   public static boolean pathToCurrentGoal() {
      if (goalPos == null) {
         return false;
      } else {
         activeTask = BuiltInBaritone.TaskKind.GOTO;
         repathTimer = 0;
         currentPath.clear();
         return true;
      }
   }

   public static void setSelection(int index, BlockPos pos) {
      if (index == 1) {
         selPos1 = pos.immutable();
      } else {
         selPos2 = pos.immutable();
      }
   }

   public static boolean clearSelectionArea() {
      if (selPos1 != null && selPos2 != null) {
         startExcavate(selPos1, selPos2);
         return true;
      } else {
         return false;
      }
   }

   public static List<BlockPos> path() {
      return Collections.unmodifiableList(currentPath);
   }

   public static void configureRender(boolean enabled, int lineColor, int boxColor) {
      renderPath = enabled;
      pathColor = lineColor;
      goalColor = boxColor;
   }

   public static boolean setSetting(String key, String value) {
      String clean = key.toLowerCase(Locale.ROOT).trim();
      boolean boolVal = "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);

      return switch (clean) {
         case "allowbreak" -> {
            allowBreak = boolVal;
            yield true;
         }
         case "allowplace" -> {
            allowPlace = boolVal;
            yield true;
         }
         case "allowsprint" -> {
            allowSprint = boolVal;
            yield true;
         }
         case "allowparkour" -> {
            allowParkour = boolVal;
            yield true;
         }
         default -> false;
      };
   }

   public static String statusSummary() {
      if (activeTask == BuiltInBaritone.TaskKind.NONE) {
         return goalPos != null ? "Goal set (" + goalPos.toShortString() + ") · Idle" : "Idle";
      } else if (BaritoneBridge.isPaused()) {
         return "Paused (" + BaritoneBridge.pauseReason() + ")";
      } else {
         String goalText = goalPos == null ? (activeTask == BuiltInBaritone.TaskKind.MINE ? " · searching" : "") : " -> " + goalPos.getX() + " " + goalPos.getY() + " " + goalPos.getZ();
         return activeTask.label + goalText + " (" + Math.max(0, currentPath.size() - pathIndex) + " nodes)";
      }
   }

   public static void stop() {
      activeTask = BuiltInBaritone.TaskKind.NONE;
      goalPos = null;
      currentPath.clear();
      pathIndex = 0;
      stuckTicks = 0;
      failedPaths = 0;
      mineTargets.clear();
      mineScanner = null;
      farmScanner = null;
      followFilter = null;
      pathGoal = null;
      pathAge = 0;
      chunkWait = 0;
      held = false;
      VIEW.release();
      costPlayer = null;
      releaseControls();
      stopMining(Minecraft.getInstance());
   }

   public static void gotoBlock(BlockPos target) {
      stop();
      activeTask = BuiltInBaritone.TaskKind.GOTO;
      goalPos = target.immutable();
      repathTimer = 0;
      targetSince = clock;
   }

   public static void gotoXZ(int x, int z) {
      Minecraft client = Minecraft.getInstance();
      int y = client.level != null && client.player != null ? findSurfaceY(client.level, x, z, client.player.getBlockY()) : 64;
      gotoBlock(new BlockPos(x, y, z));
   }

   public static void thisWay(LocalPlayer player, int distance) {
      double yawRad = Math.toRadians(player.getYRot());
      int gx = Mth.floor(player.getX() - Math.sin(yawRad) * distance);
      int gz = Mth.floor(player.getZ() + Math.cos(yawRad) * distance);
      int gy = findSurfaceY(player.level(), gx, gz, player.getBlockY());
      setGoalOnly(new BlockPos(gx, gy, gz));
   }

   public static void gotoAxis(LocalPlayer player) {
      int x = player.getBlockX();
      int z = player.getBlockZ();
      int absX = Math.abs(x);
      int absZ = Math.abs(z);
      int gx;
      int gz;
      if (absX <= absZ) {
         gx = 0;
         gz = z;
      } else {
         gx = x;
         gz = 0;
      }

      int gy = findSurfaceY(player.level(), gx, gz, player.getBlockY());
      gotoBlock(new BlockPos(gx, gy, gz));
   }

   public static boolean invertGoal(LocalPlayer player) {
      if (goalPos == null) {
         return false;
      } else {
         int dx = player.getBlockX() - goalPos.getX();
         int dz = player.getBlockZ() - goalPos.getZ();
         int gx = player.getBlockX() + dx;
         int gz = player.getBlockZ() + dz;
         int gy = findSurfaceY(player.level(), gx, gz, player.getBlockY());
         gotoBlock(new BlockPos(gx, gy, gz));
         return true;
      }
   }

   public static void mineBlocks(Set<Block> blocks) {
      stop();
      if (blocks != null && !blocks.isEmpty()) {
         mineTargets.addAll(blocks);
         mineScanner = new ChunkScanner(state -> mineTargets.contains(state.getBlock()), 6, 4096).budgetMicros(500L);
         activeTask = BuiltInBaritone.TaskKind.MINE;
         repathTimer = 0;
      }
   }

   public static boolean mineByNames(String[] names) {
      Set<Block> resolved = new HashSet<>();

      for (String raw : names) {
         String clean = raw.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
         if (!clean.isEmpty()) {
            for (Block block : BuiltInRegistries.BLOCK) {
               String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
               if (path.equals(clean) || path.contains(clean)) {
                  resolved.add(block);
               }
            }
         }
      }

      if (resolved.isEmpty()) {
         return false;
      } else {
         mineBlocks(resolved);
         return true;
      }
   }

   public static void follow(Predicate<Entity> filter) {
      stop();
      followFilter = filter;
      activeTask = BuiltInBaritone.TaskKind.FOLLOW;
      repathTimer = 0;
   }

   public static void startTunnel(Direction dir, int length) {
      stop();
      tunnelDirection = dir;
      tunnelRemaining = Math.max(1, length);
      activeTask = BuiltInBaritone.TaskKind.TUNNEL;
   }

   public static void startExplore(BlockPos origin) {
      stop();
      exploreOrigin = origin.immutable();
      exploreStep = 1;
      activeTask = BuiltInBaritone.TaskKind.EXPLORE;
      repathTimer = 0;
   }

   public static void startExcavate(BlockPos cornerA, BlockPos cornerB) {
      stop();
      excavateMin = new BlockPos(Math.min(cornerA.getX(), cornerB.getX()), Math.min(cornerA.getY(), cornerB.getY()), Math.min(cornerA.getZ(), cornerB.getZ()));
      excavateMax = new BlockPos(Math.max(cornerA.getX(), cornerB.getX()), Math.max(cornerA.getY(), cornerB.getY()), Math.max(cornerA.getZ(), cornerB.getZ()));
      excavateX = excavateMin.getX();
      excavateY = excavateMax.getY();
      excavateZ = excavateMin.getZ();
      excavateFoundInPass = false;
      excavateSkippedInPass = 0;
      activeTask = BuiltInBaritone.TaskKind.EXCAVATE;
      repathTimer = 0;
   }

   public static void startFarm() {
      stop();
      farmScanner = new ChunkScanner(BuiltInBaritone::isRipe, 4, 1024).budgetMicros(300L);
      activeTask = BuiltInBaritone.TaskKind.FARM;
      repathTimer = 0;
   }

   public static void tick() {
      clock++;
      long now = System.nanoTime();
      if (lastTickNanos != 0L && now - lastTickNanos > LAG_SPIKE_NANOS) {
         // The game froze for a moment (chunk generation, GC, a slow frame). The player did not move because nothing
         // did, so the stuck counter is held back until movement has had time to resume.
         lagGrace = LAG_GRACE_TICKS;
      }

      lastTickNanos = now;
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null && activeTask != BuiltInBaritone.TaskKind.NONE) {
         VIEW.begin(level);
         boolean hold = BaritoneBridge.isPaused() || client.gui.screen() != null;
         if (hold) {
            if (!held) {
               held = true;
               heldSince = clock;
               stopMining(client);
            }

            releaseControls();
            return;
         }

         if (held) {
            // Back from a pause: the player may be anywhere, so the old path and the stuck bookkeeping are void, and
            // the time spent paused must not count against reaching the current target.
            held = false;
            targetSince += clock - heldSince;
            currentPath.clear();
            pathIndex = 0;
            stuckTicks = 0;
            repathTimer = 0;
            lastPlayerPos = player.position();
         }

         switch (activeTask) {
            case GOTO -> tickGoto(client, player, level);
            case MINE -> tickMine(client, player, level);
            case FOLLOW -> tickFollow(client, player, level);
            case TUNNEL -> tickTunnel(client, player, level);
            case EXPLORE -> tickExplore(client, player, level);
            case EXCAVATE -> tickExcavate(client, player, level);
            case FARM -> tickFarm(client, player, level);
            default -> {
            }
         }
      }
   }

   /** Lets go of a block that is being broken, so nothing keeps mining while the bot is paused or stopped. */
   private static void stopMining(Minecraft client) {
      if (mining()) {
         lastMineClock = Integer.MIN_VALUE / 2;
         BlockBreaking.release();
         if (client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }
      }
   }

   private static void tickGoto(Minecraft client, LocalPlayer player, Level level) {
      if (goalPos == null) {
         stop();
      } else if (player.blockPosition().distManhattan(goalPos) <= 1) {
         CommandManager.feedback("§bBaritone §7arrived at §f" + goalPos.getX() + " " + goalPos.getY() + " " + goalPos.getZ() + "§7.");
         stop();
      } else if (failedPaths >= 12) {
         CommandManager.feedback("§bBaritone §7can't find a way to §f" + goalPos.toShortString() + "§7; stopping.");
         stop();
      } else {
         followPathToGoal(client, player, level, goalPos, allowBreak);
      }
   }

   private static void tickMine(Minecraft client, LocalPlayer player, ClientLevel level) {
      if (mineTargets.isEmpty()) {
         stop();
         return;
      }

      if (mineScanner == null) {
         mineScanner = new ChunkScanner(state -> mineTargets.contains(state.getBlock()), 6, 4096).budgetMicros(500L);
      }

      mineScanner.tick(level, player.getBlockX() >> 4, player.getBlockZ() >> 4, 4);
      boolean goalValid = goalPos != null && mineTargets.contains(level.getBlockState(goalPos).getBlock());
      if (goalValid && (clock - targetSince > TARGET_GIVE_UP_TICKS || failedPaths >= 6)) {
         AVOID_TARGETS.put(goalPos.asLong(), (long)(clock + TARGET_AVOID_TICKS));
         CommandManager.feedback("§bBaritone §7couldn't reach §f" + goalPos.toShortString() + "§7, trying another one.");
         goalValid = false;
      }

      if (!goalValid) {
         BlockPos next = nearestFound(mineScanner, player, level, state -> mineTargets.contains(state.getBlock()));
         if (next == null) {
            goalPos = null;
            releaseControls();
            return;
         }

         goalPos = next;
         currentPath.clear();
         targetSince = clock;
         failedPaths = 0;
      }

      double distSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(goalPos));
      if (distSq <= 19.5) {
         releaseControls();
         mineBlockAt(client, player, goalPos);
      } else {
         followPathToGoal(client, player, level, goalPos, true);
      }
   }

   /**
    * The closest scanner hit that is not on the give-up list, favouring blocks near the player's own level. The
    * scanner lags a few ticks behind the world, so a candidate is checked against the live block before it wins.
    */
   private static BlockPos nearestFound(ChunkScanner scanner, LocalPlayer player, Level level, Predicate<BlockState> valid) {
      pickBest = null;
      pickBestScore = Double.MAX_VALUE;
      double px = player.getX();
      double py = player.getY();
      double pz = player.getZ();
      scanner.forEach((cx, cz) -> true, packed -> {
         if (AVOID_TARGETS.get(packed) <= clock) {
            double dx = BlockPos.getX(packed) + 0.5 - px;
            double dy = BlockPos.getY(packed) + 0.5 - py;
            double dz = BlockPos.getZ(packed) + 0.5 - pz;
            double score = dx * dx + dz * dz + dy * dy * 4.0;
            if (score < pickBestScore) {
               BlockPos candidate = BlockPos.of(packed);
               if (valid.test(level.getBlockState(candidate))) {
                  pickBestScore = score;
                  pickBest = candidate;
               }
            }
         }
      });
      return pickBest;
   }

   private static void tickFollow(Minecraft client, LocalPlayer player, ClientLevel level) {
      if (followFilter == null) {
         stop();
      } else {
         Entity nearest = null;
         double best = Double.MAX_VALUE;

         for (Entity entity : level.entitiesForRendering()) {
            if (entity != player && entity.isAlive() && followFilter.test(entity)) {
               double d = player.distanceToSqr(entity);
               if (d < best) {
                  best = d;
                  nearest = entity;
               }
            }
         }

         if (nearest == null) {
            releaseControls();
         } else {
            goalPos = nearest.blockPosition();
            if (best <= 7.5) {
               releaseControls();
            } else {
               followPathToGoal(client, player, level, goalPos, false);
            }
         }
      }
   }

   private static void tickTunnel(Minecraft client, LocalPlayer player, Level level) {
      if (tunnelRemaining <= 0) {
         CommandManager.feedback("§bBaritone §7tunnel completed.");
         stop();
      } else {
         BlockPos feet = player.blockPosition();
         BlockPos aheadFeet = feet.relative(tunnelDirection);
         BlockPos aheadHead = aheadFeet.above();
         if (isLava(level.getBlockState(aheadFeet)) || isLava(level.getBlockState(aheadHead)) || isLava(level.getBlockState(aheadFeet.below()))
            || touchesLava(level, aheadFeet.getX(), aheadFeet.getY(), aheadFeet.getZ())
            || touchesLava(level, aheadHead.getX(), aheadHead.getY(), aheadHead.getZ())) {
            CommandManager.feedback("§bBaritone §7tunnel stopped: lava ahead at §f" + aheadFeet.toShortString() + "§7.");
            stop();
         } else if (!passable(level, aheadHead)) {
            releaseControls();
            mineBlockAt(client, player, aheadHead);
         } else if (!passable(level, aheadFeet)) {
            releaseControls();
            mineBlockAt(client, player, aheadFeet);
         } else {
            BlockPos floorAhead = aheadFeet.below();
            if (passable(level, floorAhead) && allowPlace) {
               BlockPlacer.place(client, floorAhead, BlockPlacer::solidFiller, true, true, true);
            }

            goalPos = aheadFeet;
            steerToward(client, player, Vec3.atBottomCenterOf(aheadFeet), false);
            if (player.blockPosition().equals(aheadFeet)) {
               tunnelRemaining--;
            }
         }
      }
   }

   private static void tickExplore(Minecraft client, LocalPlayer player, Level level) {
      if (exploreOrigin == null) {
         exploreOrigin = player.blockPosition();
      }

      if (goalPos == null || player.blockPosition().distManhattan(goalPos) <= 3 || repathTimer > 140 || failedPaths >= 6) {
         double angle = exploreStep * 1.15;
         int radius = 16 + exploreStep * 10;
         int gx = exploreOrigin.getX() + (int)Math.round(Math.cos(angle) * radius);
         int gz = exploreOrigin.getZ() + (int)Math.round(Math.sin(angle) * radius);
         int gy = findSurfaceY(level, gx, gz, player.getBlockY());
         goalPos = new BlockPos(gx, gy, gz);
         exploreStep++;
         currentPath.clear();
         repathTimer = 0;
         failedPaths = 0;
      }

      followPathToGoal(client, player, level, goalPos, allowBreak);
   }

   private static void tickExcavate(Minecraft client, LocalPlayer player, ClientLevel level) {
      if (excavateMin == null || excavateMax == null) {
         stop();
         return;
      }

      if (goalPos != null) {
         BlockState state = level.getBlockState(goalPos);
         if (state.isAir() || !breakable(level, goalPos, state)) {
            goalPos = null;
         } else if (clock - targetSince > TARGET_GIVE_UP_TICKS || failedPaths >= 6) {
            AVOID_TARGETS.put(goalPos.asLong(), (long)(clock + TARGET_AVOID_TICKS));
            goalPos = null;
         }
      }

      if (goalPos == null) {
         // resume the top-down sweep where it stopped; a few thousand blocks per tick keeps big boxes smooth
         for (int scanned = 0; goalPos == null && scanned < 4096; scanned++) {
            BlockState state = level.getBlockState(SCRATCH.set(excavateX, excavateY, excavateZ));
            if (!state.isAir() && breakable(level, SCRATCH, state)) {
               if (AVOID_TARGETS.get(BlockPos.asLong(excavateX, excavateY, excavateZ)) > clock || !safeToDig(player, level, excavateX, excavateY, excavateZ)) {
                  excavateSkippedInPass++;
               } else {
                  goalPos = new BlockPos(excavateX, excavateY, excavateZ);
                  excavateFoundInPass = true;
                  currentPath.clear();
                  targetSince = clock;
                  failedPaths = 0;
               }
            }

            if (advanceExcavateCursor()) {
               if (!excavateFoundInPass) {
                  CommandManager.feedback(
                     excavateSkippedInPass > 0
                        ? "§bBaritone §7excavation stopped: the remaining blocks sit on lava or over a drop."
                        : "§bBaritone §7excavation finished."
                  );
                  stop();
                  return;
               }

               excavateFoundInPass = false;
               excavateSkippedInPass = 0;
            }
         }

         if (goalPos == null) {
            releaseControls();
            return;
         }
      }

      if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(goalPos)) <= 19.5) {
         releaseControls();
         mineBlockAt(client, player, goalPos);
      } else {
         followPathToGoal(client, player, level, goalPos, true);
      }
   }

   /** Moves the excavate cursor one block (z, then x, then down a layer); true when a whole pass just completed. */
   private static boolean advanceExcavateCursor() {
      if (++excavateZ > excavateMax.getZ()) {
         excavateZ = excavateMin.getZ();
         if (++excavateX > excavateMax.getX()) {
            excavateX = excavateMin.getX();
            if (--excavateY < excavateMin.getY()) {
               excavateY = excavateMax.getY();
               return true;
            }
         }
      }

      return false;
   }

   /** Never dig into lava, and never open the floor under the player's feet onto lava or a long drop. */
   private static boolean safeToDig(LocalPlayer player, Level level, int x, int y, int z) {
      if (touchesLava(level, x, y, z)) {
         return false;
      } else if (x == player.getBlockX() && z == player.getBlockZ() && y == player.getBlockY() - 1) {
         BlockState under = state(level, x, y - 1, z);
         return !isLava(under) && !passable(level, under, x, y - 1, z);
      } else {
         return true;
      }
   }

   private static void tickFarm(Minecraft client, LocalPlayer player, ClientLevel level) {
      if (farmScanner == null) {
         farmScanner = new ChunkScanner(BuiltInBaritone::isRipe, 4, 1024).budgetMicros(300L);
      }

      farmScanner.tick(level, player.getBlockX() >> 4, player.getBlockZ() >> 4, 2);
      boolean goalValid = goalPos != null && isRipe(level.getBlockState(goalPos));
      if (goalValid && (clock - targetSince > TARGET_GIVE_UP_TICKS / 2 || failedPaths >= 6)) {
         AVOID_TARGETS.put(goalPos.asLong(), (long)(clock + TARGET_AVOID_TICKS));
         goalValid = false;
      }

      if (!goalValid) {
         goalPos = nearestFound(farmScanner, player, level, BuiltInBaritone::isRipe);
         currentPath.clear();
         targetSince = clock;
         failedPaths = 0;
      }

      if (goalPos == null) {
         releaseControls();
         if ((clock & 7) == 0) {
            replant(client, player, level);
         }
      } else if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(goalPos)) <= 16.0) {
         releaseControls();
         mineBlockAt(client, player, goalPos);
      } else {
         followPathToGoal(client, player, level, goalPos, false);
      }
   }

   private static boolean isRipe(BlockState state) {
      return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
   }

   /** Plants a seed from the hotbar on the nearest empty farmland within reach. */
   private static void replant(Minecraft client, LocalPlayer player, Level level) {
      if (client.gameMode == null) {
         return;
      }

      int seedSlot = Inventories.findHotbar(player, s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS));
      if (seedSlot < 0) {
         return;
      }

      Vec3 eye = player.getEyePosition();
      int px = player.getBlockX();
      int py = player.getBlockY();
      int pz = player.getBlockZ();
      BlockPos best = null;
      double bestDist = 20.25;

      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            for (int dy = -2; dy <= 1; dy++) {
               int x = px + dx;
               int y = py + dy;
               int z = pz + dz;
               if (state(level, x, y, z).is(Blocks.FARMLAND) && state(level, x, y + 1, z).isAir()) {
                  double d = eye.distanceToSqr(new Vec3(x + 0.5, y + 1.0, z + 0.5));
                  if (d < bestDist) {
                     bestDist = d;
                     best = new BlockPos(x, y, z);
                  }
               }
            }
         }
      }

      if (best != null && Hands.claimMainHand(BuiltInBaritone.class, ActionPriority.BOT)) {
         int prev = player.getInventory().getSelectedSlot();
         player.getInventory().setSelectedSlot(seedSlot);
         Vec3 hit = Vec3.atCenterOf(best).add(0.0, 0.5, 0.0);
         client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
         Swing.interact(player, InteractionHand.MAIN_HAND);
         player.getInventory().setSelectedSlot(prev);
      }
   }

   private static void mineBlockAt(Minecraft client, LocalPlayer player, BlockPos pos) {
      if (client.gameMode != null && client.level != null) {
         BlockState state = client.level.getBlockState(pos);
         if (state.isAir()) {
            return;
         }

         equipBestTool(player, state);
         float[] rot = RotationManager.toward(player, pos);
         RotationManager.request(player, rot[0], rot[1], 80.0F, true, ActionPriority.BOT, 8.0F);
         BlockBreaking.keep();
         client.gameMode.continueDestroyBlock(pos, Direction.UP);
         Swing.attack(player, InteractionHand.MAIN_HAND);
         lastMineClock = clock;
      }
   }

   /** Whether the bot was breaking a block this tick or the one before (standing still then is not being stuck). */
   private static boolean mining() {
      return clock - lastMineClock <= 1;
   }

   /** Switches to the fastest hotbar tool, unless a more important module already took the hand this tick. */
   private static void equipBestTool(LocalPlayer player, BlockState state) {
      int best = Tools.bestSlot(player, state, true);
      if (best != player.getInventory().getSelectedSlot() && Hands.claimMainHand(BuiltInBaritone.class, ActionPriority.BOT)) {
         player.getInventory().setSelectedSlot(best);
      }
   }

   private static void followPathToGoal(Minecraft client, LocalPlayer player, Level level, BlockPos target, boolean canBreakWalls) {
      if (!VIEW.loaded(player.getBlockX(), player.getBlockZ())) {
         // Standing in a chunk that has not arrived yet (just teleported, changed dimension): nothing to plan on.
         releaseControls();
         return;
      }

      if (chunkWait > 0) {
         chunkWait--;
         releaseControls();
         return;
      }

      repathTimer++;
      pathAge++;
      if (lagGrace > 0) {
         lagGrace--;
         stuckTicks = 0;
      } else if (mining() || player.position().distanceToSqr(lastPlayerPos) >= 0.004) {
         stuckTicks = 0;
      } else {
         stuckTicks++;
      }

      lastPlayerPos = player.position();
      boolean stuck = stuckTicks >= 20;
      if (stuck && !currentPath.isEmpty()) {
         // remember where we got stuck so the next search routes around it instead of trying the same jump again
         BlockPos node = currentPath.get(Math.min(pathIndex, currentPath.size() - 1));
         AVOID.put(node.asLong(), (long)(clock + AVOID_TICKS));
         if (pathIndex + 1 < currentPath.size()) {
            AVOID.put(currentPath.get(pathIndex + 1).asLong(), (long)(clock + AVOID_TICKS));
         }
      }

      boolean finished = currentPath.isEmpty() || pathIndex >= currentPath.size();
      boolean review = repathTimer >= REVIEW_TICKS;
      if (review) {
         repathTimer = 0;
      }

      // A path is searched again when it ran out, when the player is stuck, or when a review finds it no longer
      // fits: it was only partial, the target moved, a block ahead changed, or it is simply old.
      boolean stale = review
         && (!pathComplete || pathAge >= MAX_PATH_AGE_TICKS || pathGoal == null || pathGoal.distManhattan(target) > 2 || !pathStillValid(level, canBreakWalls));
      if (finished || stuck || stale) {
         repathTimer = 0;
         stuckTicks = 0;
         List<BlockPos> computed = computePath(level, player.blockPosition(), target, canBreakWalls);
         currentPath.clear();
         currentPath.addAll(computed);
         pathIndex = 0;
         pathAge = 0;
         pathGoal = target.immutable();
         if (!computed.isEmpty()) {
            failedPaths = 0;
         } else if (pathHitUnloaded) {
            // The way on lies in chunks that have not loaded. Wait for them; this is not a dead end.
            chunkWait = CHUNK_WAIT_TICKS;
            releaseControls();
            return;
         } else {
            failedPaths++;
         }
      }

      if (currentPath.isEmpty()) {
         steerToward(client, player, Vec3.atBottomCenterOf(target), player.horizontalCollision);
      } else {
         while (true) {
            if (pathIndex < currentPath.size() - 1) {
               BlockPos node = currentPath.get(pathIndex);
               double dx = node.getX() + 0.5 - player.getX();
               double dz = node.getZ() + 0.5 - player.getZ();
               if (dx * dx + dz * dz < 0.36 && Math.abs(node.getY() - player.getY()) <= 1.35) {
                  pathIndex++;
                  continue;
               }
            }

            BlockPos nextNode = currentPath.get(Math.min(pathIndex, currentPath.size() - 1));
            BlockPos headBlock = nextNode.above();
            if (nextNode.getY() > player.getBlockY() && !passable(level, player.blockPosition().above(2))) {
               releaseControls();
               mineBlockAt(client, player, player.blockPosition().above(2));
               break;
            }

            if (!passable(level, headBlock)) {
               releaseControls();
               mineBlockAt(client, player, headBlock);
            } else if (!passable(level, nextNode)) {
               releaseControls();
               mineBlockAt(client, player, nextNode);
            } else {
               if (nextNode.getY() < player.getBlockY() && nextNode.getX() == player.getBlockX() && nextNode.getZ() == player.getBlockZ()) {
                  BlockPos belowFeet = player.blockPosition().below();
                  if (!passable(level, belowFeet)) {
                     releaseControls();
                     mineBlockAt(client, player, belowFeet);
                     return;
                  }
               }

               BlockPos floor = nextNode.below();
               if (allowPlace && passable(level, floor) && !isWater(level.getBlockState(floor)) && !climbable(level, nextNode)) {
                  BlockPlacer.place(client, floor, BlockPlacer::solidFiller, true, true, true);
               }

               if (nextNode.getY() > player.getBlockY() && nextNode.getX() == player.getBlockX() && nextNode.getZ() == player.getBlockZ()) {
                  if (climbable(level, player.blockPosition()) || climbable(level, nextNode)) {
                     steerToward(client, player, new Vec3(nextNode.getX() + 0.5, nextNode.getY(), nextNode.getZ() + 0.5), true);
                     return;
                  }

                  if (allowPlace) {
                     player.jumpFromGround();
                     BlockPlacer.place(client, player.blockPosition(), BlockPlacer::solidFiller, true, true, true, false);
                     return;
                  }
               }

               double horizDistSq = Math.pow(nextNode.getX() + 0.5 - player.getX(), 2.0) + Math.pow(nextNode.getZ() + 0.5 - player.getZ(), 2.0);
               boolean parkourLeap = horizDistSq > 1.75 && player.onGround();
               boolean jump = nextNode.getY() > player.getBlockY() || player.horizontalCollision || parkourLeap;
               steerToward(client, player, new Vec3(nextNode.getX() + 0.5, nextNode.getY(), nextNode.getZ() + 0.5), jump);
            }
            break;
         }
      }
   }

   private static void steerToward(Minecraft client, LocalPlayer player, Vec3 point, boolean jump) {
      double dx = point.x - player.getX();
      double dz = point.z - player.getZ();
      float targetYaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
      float smoothYaw = Mth.rotLerp(0.42F, player.getYRot(), Mth.wrapDegrees(targetYaw));
      player.setYRot(smoothYaw);
      player.yHeadRot = smoothYaw;
      client.options.keyUp.setDown(true);
      client.options.keySprint.setDown(allowSprint);
      client.options.keyJump.setDown(jump || player.isInWater());
      if (allowSprint && !player.isCrouching()) {
         player.setSprinting(true);
      }
   }

   private static void releaseControls() {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.options != null) {
         client.options.keyUp.setDown(MoveUtil.physicalKeyDown(client.options.keyUp));
         client.options.keyJump.setDown(MoveUtil.physicalKeyDown(client.options.keyJump));
         client.options.keySprint.setDown(MoveUtil.physicalKeyDown(client.options.keySprint));
      }
   }

   public static List<BlockPos> computePath(Level level, BlockPos start, BlockPos goal) {
      return computePath(level, start, goal, allowBreak);
   }

   /**
    * A* from {@code start} to within one block of {@code goal}. Returns the path (without the start) or, when the
    * goal is out of reach within the iteration and time budget, the path to the explored node closest to it, so the
    * caller still makes progress and searches again from there.
    */
   public static List<BlockPos> computePath(Level level, BlockPos start, BlockPos goal, boolean canBreak) {
      Minecraft client = Minecraft.getInstance();
      costPlayer = client == null ? null : client.player;
      BREAK_COSTS.clear();
      pruneAvoided();
      VIEW.begin(level);
      searchCanBreak = canBreak;
      long started = System.nanoTime();
      int steps = SEARCH.search(
         start.getX(), start.getY(), start.getZ(), goal.getX(), goal.getY(), goal.getZ(), 1, MAX_A_STAR_ITERATIONS, started + PATH_BUDGET_NANOS, MOVES
      );
      lastSearchNanos = System.nanoTime() - started;
      lastSearchNodes = SEARCH.expanded();
      pathComplete = SEARCH.reachedGoal();
      pathHitUnloaded = VIEW.touchedUnloaded();
      List<BlockPos> path = new ArrayList<>(steps);
      for (int i = 0; i < steps; i++) {
         path.add(new BlockPos(SEARCH.stepX(i), SEARCH.stepY(i), SEARCH.stepZ(i)));
      }

      return path;
   }

   /** Nodes expanded and microseconds spent by the last search, for {@code #status} and profiling. */
   public static String searchStats() {
      return lastSearchNodes + " nodes in " + lastSearchNanos / 1000L + " µs" + (pathComplete ? "" : ", partial") + (pathHitUnloaded ? ", at the loaded edge" : "");
   }

   /** True while the next {@link #PATH_LOOKAHEAD} nodes of the current path can still be walked (or dug, when allowed). */
   private static boolean pathStillValid(Level level, boolean canBreak) {
      int end = Math.min(currentPath.size(), pathIndex + PATH_LOOKAHEAD);
      for (int i = pathIndex; i < end; i++) {
         BlockPos node = currentPath.get(i);
         int x = node.getX();
         int y = node.getY();
         int z = node.getZ();
         BlockState feet = state(level, x, y, z);
         BlockState head = state(level, x, y + 1, z);
         if (isLava(feet) || isLava(head) || isLava(state(level, x, y - 1, z))) {
            return false;
         }

         if (!canBreak && (!passable(level, feet, x, y, z) || !passable(level, head, x, y + 1, z))) {
            return false;
         }
      }

      return true;
   }

   /** Every move out of {@code x, y, z}; the {@link PathSearch} calls this once per expanded node. */
   private static void expand(int x, int y, int z, PathSearch.Sink sink) {
      Level level = VIEW.level();
      boolean canBreak = searchCanBreak;
      // the hazard of standing here (lava next to the feet) is charged once, on every move that leaves
      double leave = leaveCost(level, x, y, z);

      for (int vdy : VERTICAL_STEPS) {
         double vCost = verticalShaftCost(level, x, y, z, vdy, canBreak);
         if (vCost >= 0.0) {
            offer(sink, x, y + vdy, z, vCost + leave);
         }
      }

      for (int[] off : OFFSETS_8) {
         int ox = off[0];
         int oz = off[1];
         boolean diagonal = ox != 0 && oz != 0;
         if (diagonal && !cornerClear(level, x, y, z, ox, oz)) {
            continue;
         }

         for (int dy = 1; dy >= -3; dy--) {
            if (!diagonal || dy == 0) {
               double moveCost = traversalCost(level, x, y, z, ox, dy, oz, diagonal, canBreak);
               if (moveCost >= 0.0) {
                  offer(sink, x + ox, y + dy, z + oz, moveCost + leave);
                  if (dy <= 0 && passableAt(level, x + ox, y + dy, z + oz) && !passableAt(level, x + ox, y + dy - 1, z + oz)) {
                     break; // found the floor on this side; no need to look further down
                  }
               }
            }
         }
      }

      if (allowParkour && passableAt(level, x, y + 2, z)) {
         for (int[] card : CARDINAL_4) {
            int ox = card[0];
            int oz = card[1];
            if (!gapClear(level, x + ox, y, z + oz)) {
               continue;
            }

            int lx = x + 2 * ox;
            int lz = z + 2 * oz;
            if (isStandable(level, lx, y, lz)) {
               offer(sink, lx, y, lz, 2.35 + leave);
            } else if (isStandable(level, lx, y - 1, lz) && passableAt(level, lx, y + 1, lz)) {
               offer(sink, lx, y - 1, lz, 2.6 + leave);
            } else if (allowSprint && gapClear(level, lx, y, lz)) {
               int l2x = x + 3 * ox;
               int l2z = z + 3 * oz;
               if (isStandable(level, l2x, y, l2z)) {
                  offer(sink, l2x, y, l2z, 3.6 + leave);
               }
            }
         }
      }
   }

   private static void offer(PathSearch.Sink sink, int x, int y, int z, double cost) {
      if (AVOID.get(BlockPos.asLong(x, y, z)) > clock) {
         cost += AVOID_PENALTY;
      }

      sink.offer(x, y, z, cost);
   }

   /** Expired entries are ignored on read; the maps are only emptied now and then so they cannot grow forever. */
   private static void pruneAvoided() {
      if (AVOID.size() > 2048 || (clock & 2047) == 0) {
         AVOID.clear();
      }

      if (AVOID_TARGETS.size() > 2048) {
         AVOID_TARGETS.clear();
      }
   }

   /** Cost of a straight-up pillar or straight-down dig from {@code x, y, z}, or -1 when not possible. */
   private static double verticalShaftCost(Level level, int x, int y, int z, int dy, boolean canBreak) {
      if (dy > 0) {
         if (climbable(state(level, x, y, z)) || climbable(state(level, x, y + 1, z))) {
            return passableAt(level, x, y + 2, z) ? 1.35 : -1.0;
         } else if (!allowPlace) {
            return -1.0;
         } else {
            BlockState ceiling = state(level, x, y + 2, z);
            if (passable(level, ceiling, x, y + 2, z)) {
               return 2.6;
            } else if (!canBreak || falling(state(level, x, y + 3, z))) {
               return -1.0; // sand or gravel would pour onto our head
            } else {
               double dig = breakCost(level, x, y + 2, z, ceiling);
               return dig < 0.0 || touchesLava(level, x, y + 2, z) ? -1.0 : 4.5 + dig;
            }
         }
      } else if (!canBreak) {
         return -1.0;
      } else {
         BlockState floor = state(level, x, y - 1, z);
         BlockState underFloor = state(level, x, y - 2, z);
         if (isLava(underFloor) || underFloor.isAir() || isWater(underFloor) || touchesLava(level, x, y - 1, z)) {
            return -1.0;
         }

         double dig = breakCost(level, x, y - 1, z, floor);
         return dig < 0.0 ? -1.0 : 3.4 + dig;
      }
   }

   /** Cost of moving from {@code fromX, fromY, fromZ} to the block {@code ox,dy,oz} away, or -1 when the move is not possible. */
   private static double traversalCost(Level level, int fromX, int fromY, int fromZ, int ox, int dy, int oz, boolean diagonal, boolean canBreak) {
      int x = fromX + ox;
      int y = fromY + dy;
      int z = fromZ + oz;
      BlockState feet = state(level, x, y, z);
      BlockState head = state(level, x, y + 1, z);
      BlockState below = state(level, x, y - 1, z);
      if (isLava(feet) || isLava(head) || isLava(below) || hot(below)) {
         return -1.0;
      }

      double cost = diagonal ? PathSearch.DIAGONAL : 1.0;
      if (dy > 0) {
         cost += 0.65;
         double clearance = blockCost(level, fromX, fromY + 2, fromZ, canBreak);
         if (clearance < 0.0) {
            return -1.0;
         }

         cost += clearance;
      } else if (dy < 0) {
         cost += 0.3 * -dy;
         // walking off the edge: everything between the edge and the landing must already be open
         for (int k = fromY + 1; k > y + 1; k--) {
            if (!passableAt(level, x, k, z)) {
               return -1.0;
            }
         }
      }

      double feetCost = blockCost(level, x, y, z, feet, canBreak);
      if (feetCost < 0.0) {
         return -1.0;
      }

      double headCost = blockCost(level, x, y + 1, z, head, canBreak);
      if (headCost < 0.0) {
         return -1.0;
      }

      cost += feetCost + headCost;
      boolean solidFloor = VIEW.floor(below, x, y - 1, z) || isWater(below) || climbable(feet);
      if (!solidFloor) {
         if (!allowPlace || diagonal || dy != 0) {
            return -1.0;
         }

         cost += 3.1;
      }

      if (isWater(feet)) {
         cost += isWater(head) ? 2.5 : 1.2;
      }

      return cost;
   }

   private static double blockCost(Level level, int x, int y, int z, boolean canBreak) {
      return blockCost(level, x, y, z, state(level, x, y, z), canBreak);
   }

   /** 0 when the block is already open, the time to break it when we may, -1 when it blocks the way. */
   private static double blockCost(Level level, int x, int y, int z, BlockState state, boolean canBreak) {
      if (passable(level, state, x, y, z)) {
         return 0.0;
      } else if (!canBreak) {
         return -1.0;
      }

      double cost = breakCost(level, x, y, z, state);
      if (cost < 0.0 || touchesLava(level, x, y, z)) {
         return -1.0;
      }

      if (falling(state(level, x, y + 1, z))) {
         cost += 6.0; // the sand above pours into the gap and has to be dug again
      }

      return cost;
   }

   /** Blocks walked that breaking this block is worth, from the best hotbar tool's break time; -1 when unbreakable. */
   private static double breakCost(Level level, int x, int y, int z, BlockState state) {
      double cached = BREAK_COSTS.getDouble(state);
      if (cached == cached) {
         return cached;
      }

      double cost;
      if (!breakable(level, SCRATCH.set(x, y, z), state)) {
         cost = -1.0;
      } else {
         int ticks = Tools.breakTicks(costPlayer, level, SCRATCH, state);
         cost = ticks == Integer.MAX_VALUE ? -1.0 : 1.5 + Math.min(60.0, ticks / 4.0);
      }

      BREAK_COSTS.put(state, cost);
      return cost;
   }

   /** Penalty for standing at a spot with lava beside the feet. */
   private static double leaveCost(Level level, int x, int y, int z) {
      double cost = 0.0;
      for (int[] card : CARDINAL_4) {
         if (isLava(state(level, x + card[0], y, z + card[1]))) {
            cost += 3.0;
         }
      }

      return cost;
   }

   private static boolean cornerClear(Level level, int x, int y, int z, int ox, int oz) {
      return passableAt(level, x + ox, y, z) && passableAt(level, x + ox, y + 1, z) && passableAt(level, x, y, z + oz) && passableAt(level, x, y + 1, z + oz);
   }

   /** A column we can jump across: open at feet, head and jump height, and nothing to stand on. */
   private static boolean gapClear(Level level, int x, int y, int z) {
      return passableAt(level, x, y, z) && passableAt(level, x, y + 1, z) && passableAt(level, x, y + 2, z) && passableAt(level, x, y - 1, z);
   }

   private static boolean climbable(Level level, BlockPos pos) {
      return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
   }

   private static boolean climbable(BlockState state) {
      return VIEW.is(state, 0, 0, 0, BlockView.CLIMBABLE);
   }

   private static boolean falling(BlockState state) {
      return VIEW.is(state, 0, 0, 0, BlockView.FALLING);
   }

   private static boolean hot(BlockState state) {
      return VIEW.is(state, 0, 0, 0, BlockView.HOT);
   }

   private static boolean breakable(Level level, BlockPos pos, BlockState state) {
      if (!state.isAir() && !VIEW.is(state, pos.getX(), pos.getY(), pos.getZ(), BlockView.LIQUID)) {
         float speed = state.getDestroySpeed(level, pos);
         return speed >= 0.0F && speed < 45.0F;
      } else {
         return false;
      }
   }

   private static boolean touchesLava(Level level, int x, int y, int z) {
      return isLava(state(level, x + 1, y, z))
         || isLava(state(level, x - 1, y, z))
         || isLava(state(level, x, y, z + 1))
         || isLava(state(level, x, y, z - 1))
         || isLava(state(level, x, y + 1, z))
         || isLava(state(level, x, y - 1, z));
   }

   private static boolean isLava(BlockState state) {
      return VIEW.is(state, 0, 0, 0, BlockView.LAVA);
   }

   private static boolean isWater(BlockState state) {
      return VIEW.is(state, 0, 0, 0, BlockView.WATER);
   }

   /** One block through the pathfinder's chunk cache. Unloaded chunks read as an unbreakable wall, not as air. */
   private static BlockState state(Level level, int x, int y, int z) {
      if (VIEW.level() != level) {
         VIEW.begin(level);
      }

      return VIEW.state(x, y, z);
   }

   public static int findSurfaceY(Level level, int x, int z, int fallbackY) {
      for (int y = fallbackY + 16; y >= fallbackY - 24; y--) {
         if (isStandable(level, x, y, z)) {
            return y;
         }
      }

      return fallbackY;
   }

   private static boolean isStandable(Level level, int x, int y, int z) {
      if (passableAt(level, x, y, z) && passableAt(level, x, y + 1, z)) {
         BlockState below = state(level, x, y - 1, z);
         if (isLava(below) || hot(below)) {
            return false;
         }

         return VIEW.floor(below, x, y - 1, z) || isWater(below);
      } else {
         return false;
      }
   }

   private static boolean passable(Level level, BlockPos pos) {
      return passableAt(level, pos.getX(), pos.getY(), pos.getZ());
   }

   private static boolean passableAt(Level level, int x, int y, int z) {
      return passable(level, state(level, x, y, z), x, y, z);
   }

   private static boolean passable(Level level, BlockState state, int x, int y, int z) {
      if (VIEW.level() != level) {
         VIEW.begin(level);
      }

      return VIEW.passable(state, x, y, z);
   }

   /**
    * Brute-force nearest block search for one-off callers. The bot tasks use the incremental scanners instead;
    * keep the radius small here because every block in the cube is read at once.
    */
   public static BlockPos findNearestBlock(Level level, BlockPos origin, int radius, Predicate<BlockState> filter) {
      MutableBlockPos cursor = new MutableBlockPos();
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;
      int r = Math.min(radius, 32);
      int ry = Math.min(r, 16);

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -ry; dy <= ry; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               double d2 = dx * dx + dy * dy + dz * dz;
               if (d2 < bestDist) {
                  cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                  if (filter.test(level.getBlockState(cursor))) {
                     bestDist = d2;
                     best = cursor.immutable();
                  }
               }
            }
         }
      }

      return best;
   }

   public static void render3D() {
      if (renderPath && (activeTask != BuiltInBaritone.TaskKind.NONE || goalPos != null)) {
         Vec3 previous = null;
         for (int i = pathIndex; i < currentPath.size(); i++) {
            BlockPos node = currentPath.get(i);
            Vec3 point = new Vec3(node.getX() + 0.5, node.getY() + 0.15, node.getZ() + 0.5);
            if (previous != null) {
               Render3D.line(previous, point, pathColor, 2.2F, true);
            }

            previous = point;
         }

         if (goalPos != null) {
            Render3D.block(goalPos, goalColor, true, true, 2.0F, 55, true);
         }
      }
   }

   public static enum TaskKind {
      NONE("Idle"),
      GOTO("Goto"),
      MINE("Mine"),
      FOLLOW("Follow"),
      TUNNEL("Tunnel"),
      EXPLORE("Explore"),
      EXCAVATE("Excavate"),
      FARM("Farm");

      public final String label;

      private TaskKind(String label) {
         this.label = label;
      }
   }
}
