package gemini.util;

import gemini.command.CommandManager;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BuiltInBaritone {
   private static final int MAX_A_STAR_ITERATIONS = 3600;
   private static final int[] VERTICAL_STEPS = new int[]{1, -1};
   private static final int[][] OFFSETS_8 = new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {1, -1}, {1, 1}, {-1, -1}, {-1, 1}};
   private static final int[][] CARDINAL_4 = new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
   private static BuiltInBaritone.TaskKind activeTask = BuiltInBaritone.TaskKind.NONE;
   private static BlockPos goalPos;
   private static BlockPos selPos1;
   private static BlockPos selPos2;
   private static final List<BlockPos> currentPath = new ArrayList<>();
   private static int pathIndex;
   private static int repathTimer;
   private static int stuckTicks;
   private static Vec3 lastPlayerPos = Vec3.ZERO;
   private static final Set<Block> mineTargets = new HashSet<>();
   private static Predicate<Entity> followFilter;
   private static Direction tunnelDirection = Direction.NORTH;
   private static int tunnelRemaining;
   private static BlockPos excavateMin;
   private static BlockPos excavateMax;
   private static BlockPos exploreOrigin;
   private static int exploreStep;
   private static boolean allowBreak = true;
   private static boolean allowPlace = true;
   private static boolean allowSprint = true;
   private static boolean allowParkour = true;
   private static boolean renderPath = true;
   private static int pathColor = -16722689;
   private static int goalColor = -12161025;

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
         String goalText = goalPos == null ? "" : " -> " + goalPos.getX() + " " + goalPos.getY() + " " + goalPos.getZ();
         return activeTask.label + goalText + " (" + Math.max(0, currentPath.size() - pathIndex) + " nodes)";
      }
   }

   public static void stop() {
      activeTask = BuiltInBaritone.TaskKind.NONE;
      goalPos = null;
      currentPath.clear();
      pathIndex = 0;
      stuckTicks = 0;
      mineTargets.clear();
      followFilter = null;
      releaseControls();
   }

   public static void gotoBlock(BlockPos target) {
      stop();
      activeTask = BuiltInBaritone.TaskKind.GOTO;
      goalPos = target.immutable();
      repathTimer = 0;
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
      activeTask = BuiltInBaritone.TaskKind.EXCAVATE;
      repathTimer = 0;
   }

   public static void startFarm() {
      stop();
      activeTask = BuiltInBaritone.TaskKind.FARM;
      repathTimer = 0;
   }

   public static void tick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null && activeTask != BuiltInBaritone.TaskKind.NONE) {
         if (!BaritoneBridge.isPaused() && client.gui.screen() == null) {
            switch (activeTask) {
               case GOTO:
                  tickGoto(client, player, level);
                  break;
               case MINE:
                  tickMine(client, player, level);
                  break;
               case FOLLOW:
                  tickFollow(client, player, level);
                  break;
               case TUNNEL:
                  tickTunnel(client, player, level);
                  break;
               case EXPLORE:
                  tickExplore(client, player, level);
                  break;
               case EXCAVATE:
                  tickExcavate(client, player, level);
                  break;
               case FARM:
                  tickFarm(client, player, level);
            }
         } else {
            releaseControls();
         }
      }
   }

   private static void tickGoto(Minecraft client, LocalPlayer player, Level level) {
      if (goalPos == null) {
         stop();
      } else if (player.blockPosition().distManhattan(goalPos) <= 1) {
         CommandManager.feedback("§bBaritone §7arrived at §f" + goalPos.getX() + " " + goalPos.getY() + " " + goalPos.getZ() + "§7.");
         stop();
      } else {
         followPathToGoal(client, player, level, goalPos, allowBreak);
      }
   }

   private static void tickMine(Minecraft client, LocalPlayer player, Level level) {
      if (mineTargets.isEmpty()) {
         stop();
      } else {
         if (goalPos == null || !mineTargets.contains(level.getBlockState(goalPos).getBlock())) {
            goalPos = findNearestBlock(level, player.blockPosition(), 64, state -> mineTargets.contains(state.getBlock()));
            currentPath.clear();
            if (goalPos == null) {
               releaseControls();
               return;
            }
         }

         double distSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(goalPos));
         if (distSq <= 19.5) {
            releaseControls();
            mineBlockAt(client, player, goalPos);
         } else {
            followPathToGoal(client, player, level, goalPos, true);
         }
      }
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
         if (!passable(level, aheadHead)) {
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

      if (goalPos == null || player.blockPosition().distManhattan(goalPos) <= 3 || repathTimer > 140) {
         double angle = exploreStep * 1.15;
         int radius = 16 + exploreStep * 10;
         int gx = exploreOrigin.getX() + (int)Math.round(Math.cos(angle) * radius);
         int gz = exploreOrigin.getZ() + (int)Math.round(Math.sin(angle) * radius);
         int gy = findSurfaceY(level, gx, gz, player.getBlockY());
         goalPos = new BlockPos(gx, gy, gz);
         exploreStep++;
         currentPath.clear();
         repathTimer = 0;
      }

      followPathToGoal(client, player, level, goalPos, allowBreak);
   }

   private static void tickExcavate(Minecraft client, LocalPlayer player, Level level) {
      if (excavateMin != null && excavateMax != null) {
         BlockPos nextTarget = null;
         MutableBlockPos cursor = new MutableBlockPos();

         for (int y = excavateMax.getY(); y >= excavateMin.getY() && nextTarget == null; y--) {
            for (int x = excavateMin.getX(); x <= excavateMax.getX() && nextTarget == null; x++) {
               for (int z = excavateMin.getZ(); z <= excavateMax.getZ(); z++) {
                  cursor.set(x, y, z);
                  BlockState state = level.getBlockState(cursor);
                  if (!state.isAir() && breakable(level, cursor, state)) {
                     nextTarget = cursor.immutable();
                     break;
                  }
               }
            }
         }

         if (nextTarget == null) {
            CommandManager.feedback("§bBaritone §7excavation finished.");
            stop();
         } else {
            goalPos = nextTarget;
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(nextTarget)) <= 19.5) {
               releaseControls();
               mineBlockAt(client, player, nextTarget);
            } else {
               followPathToGoal(client, player, level, nextTarget, true);
            }
         }
      } else {
         stop();
      }
   }

   private static void tickFarm(Minecraft client, LocalPlayer player, Level level) {
      BlockPos ripe = findNearestBlock(level, player.blockPosition(), 36, state -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state));
      if (ripe == null) {
         BlockPos farmland = findNearestBlock(level, player.blockPosition(), 12, state -> state.is(Blocks.FARMLAND));
         if (farmland != null && level.getBlockState(farmland.above()).isAir() && player.getEyePosition().distanceToSqr(Vec3.atCenterOf(farmland)) <= 18.0) {
            int seedSlot = Inventories.findHotbar(
               player, s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS)
            );
            if (seedSlot >= 0 && client.gameMode != null) {
               int prev = player.getInventory().getSelectedSlot();
               player.getInventory().setSelectedSlot(seedSlot);
               Vec3 hit = Vec3.atCenterOf(farmland).add(0.0, 0.5, 0.0);
               client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, farmland, false));
               player.swing(InteractionHand.MAIN_HAND);
               player.getInventory().setSelectedSlot(prev);
               return;
            }
         }

         releaseControls();
      } else {
         goalPos = ripe;
         if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(ripe)) <= 16.0) {
            releaseControls();
            mineBlockAt(client, player, ripe);
         } else {
            followPathToGoal(client, player, level, ripe, false);
         }
      }
   }

   private static void mineBlockAt(Minecraft client, LocalPlayer player, BlockPos pos) {
      if (client.gameMode != null && client.level != null) {
         equipBestTool(player, client.level.getBlockState(pos));
         float[] rot = RotationManager.toward(player, pos);
         RotationManager.request(player, rot[0], rot[1], 80.0F, true, 5, 8.0F);
         client.gameMode.continueDestroyBlock(pos, Direction.UP);
         player.swing(InteractionHand.MAIN_HAND);
      }
   }

   private static void equipBestTool(LocalPlayer player, BlockState state) {
      int bestSlot = player.getInventory().getSelectedSlot();
      float bestSpeed = player.getInventory().getItem(bestSlot).getDestroySpeed(state);

      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         float speed = stack.getDestroySpeed(state);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            bestSlot = i;
         }
      }

      if (bestSlot != player.getInventory().getSelectedSlot()) {
         player.getInventory().setSelectedSlot(bestSlot);
      }
   }

   private static void followPathToGoal(Minecraft client, LocalPlayer player, Level level, BlockPos target, boolean canBreakWalls) {
      repathTimer++;
      if (player.position().distanceToSqr(lastPlayerPos) < 0.004) {
         stuckTicks++;
      } else {
         stuckTicks = 0;
      }

      lastPlayerPos = player.position();
      if (currentPath.isEmpty() || pathIndex >= currentPath.size() || repathTimer >= 32 || stuckTicks >= 20) {
         repathTimer = 0;
         stuckTicks = 0;
         List<BlockPos> computed = computePath(level, player.blockPosition(), target, canBreakWalls);
         currentPath.clear();
         currentPath.addAll(computed);
         pathIndex = 0;
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
               if (allowPlace && passable(level, floor) && !level.getBlockState(floor).is(Blocks.WATER) && !climbable(level, nextNode)) {
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

   public static List<BlockPos> computePath(Level level, BlockPos start, BlockPos goal, boolean canBreak) {
      PriorityQueue<BuiltInBaritone.Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
      Map<BlockPos, BuiltInBaritone.Node> all = new HashMap<>();
      Set<BlockPos> closed = new HashSet<>();
      BuiltInBaritone.Node startNode = new BuiltInBaritone.Node(start, null, 0.0, heuristic(start, goal));
      open.add(startNode);
      all.put(start, startNode);
      BuiltInBaritone.Node bestFallback = startNode;
      int iterations = 0;

      while (!open.isEmpty() && iterations++ < 3600) {
         BuiltInBaritone.Node current = open.poll();
         if (all.get(current.pos) == current && closed.add(current.pos)) {
            if (current.h < bestFallback.h) {
               bestFallback = current;
            }

            if (current.pos.distManhattan(goal) <= 1) {
               return reconstruct(current);
            }

            for (int vdy : VERTICAL_STEPS) {
               BlockPos vCand = current.pos.offset(0, vdy, 0);
               if (!closed.contains(vCand)) {
                  double vCost = verticalShaftCost(level, current.pos, vCand, vdy, canBreak);
                  if (vCost >= 0.0) {
                     tryOfferNode(open, all, current, vCand, current.g + vCost, goal);
                  }
               }
            }

            for (int[] off : OFFSETS_8) {
               int ox = off[0];
               int oz = off[1];
               boolean diagonal = ox != 0 && oz != 0;
               if (!diagonal || passable(level, current.pos.offset(ox, 0, 0)) && passable(level, current.pos.offset(0, 0, oz))) {
                  for (int dy = 1; dy >= -3; dy--) {
                     if (!diagonal || dy == 0) {
                        BlockPos candidate = current.pos.offset(ox, dy, oz);
                        if (!closed.contains(candidate)) {
                           double moveCost = traversalCost(level, current.pos, candidate, dy, diagonal, canBreak);
                           if (!(moveCost < 0.0)) {
                              tryOfferNode(open, all, current, candidate, current.g + moveCost, goal);
                              if (dy <= 0 && passable(level, candidate) && !passable(level, candidate.below())) {
                                 break;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (allowParkour) {
               for (int[] card : CARDINAL_4) {
                  int ox = card[0];
                  int oz = card[1];
                  BlockPos gap = current.pos.offset(ox, 0, oz);
                  BlockPos land = current.pos.offset(ox * 2, 0, oz * 2);
                  if (!closed.contains(land)
                     && passable(level, gap)
                     && passable(level, gap.above())
                     && passable(level, gap.below())
                     && isStandable(level, land)) {
                     tryOfferNode(open, all, current, land, current.g + 2.35, goal);
                  }
               }
            }
         }
      }

      return reconstruct(bestFallback);
   }

   private static void tryOfferNode(
      PriorityQueue<BuiltInBaritone.Node> open,
      Map<BlockPos, BuiltInBaritone.Node> all,
      BuiltInBaritone.Node current,
      BlockPos candidate,
      double g,
      BlockPos goal
   ) {
      BuiltInBaritone.Node existing = all.get(candidate);
      if (existing == null || g < existing.g) {
         BuiltInBaritone.Node next = new BuiltInBaritone.Node(candidate, current, g, heuristic(candidate, goal));
         all.put(candidate, next);
         open.add(next);
      }
   }

   private static double verticalShaftCost(Level level, BlockPos from, BlockPos to, int dy, boolean canBreak) {
      if (dy > 0) {
         if (climbable(level, from) || climbable(level, to)) {
            return passable(level, to.above()) ? 1.35 : -1.0;
         } else if (!allowPlace) {
            return -1.0;
         } else {
            BlockPos ceiling = from.above(2);
            if (passable(level, ceiling)) {
               return 2.6;
            } else {
               return canBreak && breakable(level, ceiling, level.getBlockState(ceiling)) && !touchesLava(level, ceiling)
                  ? 4.5 + Math.min(8.0, Math.max(0.5, (double)level.getBlockState(ceiling).getDestroySpeed(level, ceiling)))
                  : -1.0;
            }
         }
      } else if (!canBreak) {
         return -1.0;
      } else {
         BlockState floor = level.getBlockState(to);
         BlockState underFloor = level.getBlockState(to.below());
         return !underFloor.is(Blocks.LAVA) && !underFloor.isAir() && breakable(level, to, floor) && !touchesLava(level, to)
            ? 3.4 + Math.min(8.0, Math.max(0.5, (double)floor.getDestroySpeed(level, to)))
            : -1.0;
      }
   }

   private static double traversalCost(Level level, BlockPos from, BlockPos pos, int dy, boolean diagonal, boolean canBreak) {
      BlockState feet = level.getBlockState(pos);
      BlockState head = level.getBlockState(pos.above());
      BlockState below = level.getBlockState(pos.below());
      if (!feet.is(Blocks.LAVA) && !head.is(Blocks.LAVA) && !below.is(Blocks.LAVA) && !below.is(Blocks.FIRE) && !below.is(Blocks.MAGMA_BLOCK)) {
         double cost = diagonal ? 1.414 : 1.0;
         if (dy > 0) {
            cost += 0.65;
            BlockPos stepCeiling = from.above(2);
            if (!passable(level, stepCeiling)) {
               if (!canBreak || !breakable(level, stepCeiling, level.getBlockState(stepCeiling)) || touchesLava(level, stepCeiling)) {
                  return -1.0;
               }

               cost += 2.6 + Math.min(8.0, Math.max(0.5, (double)level.getBlockState(stepCeiling).getDestroySpeed(level, stepCeiling)));
            }
         } else if (dy < 0) {
            cost += 0.3 * Math.abs(dy);
         }

         if (!passable(level, pos)) {
            if (!canBreak || !breakable(level, pos, feet) || touchesLava(level, pos)) {
               return -1.0;
            }

            cost += 2.6 + Math.min(8.0, Math.max(0.5, (double)feet.getDestroySpeed(level, pos)));
         }

         if (!passable(level, pos.above())) {
            if (!canBreak || !breakable(level, pos.above(), head) || touchesLava(level, pos.above())) {
               return -1.0;
            }

            cost += 2.6 + Math.min(8.0, Math.max(0.5, (double)head.getDestroySpeed(level, pos.above())));
         }

         boolean solidFloor = !below.getCollisionShape(level, pos.below()).isEmpty() || below.is(Blocks.WATER) || climbable(level, pos);
         if (!solidFloor) {
            if (!allowPlace || diagonal || dy != 0) {
               return -1.0;
            }

            cost += 3.1;
         }

         return cost;
      } else {
         return -1.0;
      }
   }

   private static boolean climbable(Level level, BlockPos pos) {
      return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
   }

   private static boolean breakable(Level level, BlockPos pos, BlockState state) {
      if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
         float speed = state.getDestroySpeed(level, pos);
         return speed >= 0.0F && speed < 45.0F;
      } else {
         return false;
      }
   }

   private static boolean touchesLava(Level level, BlockPos pos) {
      for (Direction dir : Direction.values()) {
         if (level.getBlockState(pos.relative(dir)).is(Blocks.LAVA)) {
            return true;
         }
      }

      return false;
   }

   private static List<BlockPos> reconstruct(BuiltInBaritone.Node node) {
      List<BlockPos> result = new ArrayList<>();

      for (BuiltInBaritone.Node cursor = node; cursor != null && cursor.parent != null; cursor = cursor.parent) {
         result.add(cursor.pos);
      }

      Collections.reverse(result);
      return result;
   }

   private static double heuristic(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dy = a.getY() - b.getY();
      double dz = a.getZ() - b.getZ();
      return Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.12;
   }

   public static int findSurfaceY(Level level, int x, int z, int fallbackY) {
      MutableBlockPos cursor = new MutableBlockPos(x, fallbackY + 16, z);

      for (int y = fallbackY + 16; y >= fallbackY - 24; y--) {
         cursor.setY(y);
         if (isStandable(level, cursor)) {
            return y;
         }
      }

      return fallbackY;
   }

   private static boolean isStandable(Level level, BlockPos pos) {
      if (passable(level, pos) && passable(level, pos.above())) {
         BlockState below = level.getBlockState(pos.below());
         return !below.is(Blocks.LAVA) && !below.is(Blocks.FIRE) && !below.is(Blocks.MAGMA_BLOCK)
            ? !below.getCollisionShape(level, pos.below()).isEmpty() || below.is(Blocks.WATER)
            : false;
      } else {
         return false;
      }
   }

   private static boolean passable(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.is(Blocks.LAVA) ? false : state.isAir() || state.getCollisionShape(level, pos).isEmpty();
   }

   public static BlockPos findNearestBlock(Level level, BlockPos origin, int radius, Predicate<BlockState> filter) {
      MutableBlockPos cursor = new MutableBlockPos();
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -Math.min(radius, 32); dy <= Math.min(radius, 32); dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
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
         for (int i = pathIndex; i < currentPath.size() - 1; i++) {
            BlockPos a = currentPath.get(i);
            BlockPos b = currentPath.get(i + 1);
            Render3D.line(
               new Vec3(a.getX() + 0.5, a.getY() + 0.15, a.getZ() + 0.5), new Vec3(b.getX() + 0.5, b.getY() + 0.15, b.getZ() + 0.5), pathColor, 2.2F, true
            );
         }

         if (goalPos != null) {
            Render3D.block(goalPos, goalColor, true, true, 2.0F, 55, true);
         }
      }
   }

   private static final class Node {
      final BlockPos pos;
      final BuiltInBaritone.Node parent;
      final double g;
      final double h;
      final double f;

      Node(BlockPos pos, BuiltInBaritone.Node parent, double g, double h) {
         this.pos = pos;
         this.parent = parent;
         this.g = g;
         this.h = h;
         this.f = g + h;
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
