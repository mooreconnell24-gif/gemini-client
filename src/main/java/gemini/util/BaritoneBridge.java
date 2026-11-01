package gemini.util;

import gemini.Gemini;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

public final class BaritoneBridge {
   public static final String MOD_ID = "baritone";
   private static final double COMBAT_PRIORITY = 5.0;
   private static final Set<String> PAUSE_REASONS = ConcurrentHashMap.newKeySet();
   private static volatile boolean hooked;
   private static volatile Boolean apiAvailable;

   private BaritoneBridge() {
   }

   public static boolean externalPresent() {
      Boolean cached = apiAvailable;
      if (cached != null) {
         return cached;
      }

      try {
         Class<?> api = Class.forName("baritone.api.BaritoneAPI", false, BaritoneBridge.class.getClassLoader());
         Object provider = api.getMethod("getProvider").invoke(null);
         boolean ok = provider != null && provider.getClass().getMethod("getPrimaryBaritone").invoke(provider) != null;
         apiAvailable = ok;
         return ok;
      } catch (Throwable ignored) {
         apiAvailable = false;
         return false;
      }
   }

   public static boolean hasExternalBaritone() {
      return externalPresent();
   }

   public static boolean present() {
      return true;
   }

   public static String engineName() {
      return externalPresent() ? "Baritone API (Native)" : "Baritone 3D A* Engine";
   }

   public static boolean isPaused() {
      return !PAUSE_REASONS.isEmpty();
   }

   public static String pauseReason() {
      return PAUSE_REASONS.isEmpty() ? null : String.join(", ", PAUSE_REASONS);
   }

   public static void pause(String reason) {
      if (reason != null && !reason.isEmpty()) {
         ensureHooked();
         PAUSE_REASONS.add(reason);
      }
   }

   public static void resume(String reason) {
      if (reason != null && !reason.isEmpty()) {
         PAUSE_REASONS.remove(reason);
      }
   }

   public static void resumeAll() {
      PAUSE_REASONS.clear();
   }

   public static void init() {
      apiAvailable = null;
      ensureHooked();
   }

   public static void tick() {
      BuiltInBaritone.tick();
   }

   public static void render3D() {
      BuiltInBaritone.render3D();
   }

   public static boolean isActive() {
      if (externalPresent()) {
         try {
            Object baritone = primaryBaritone();
            Object behavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            if ((Boolean)behavior.getClass().getMethod("isPathing").invoke(behavior)) {
               return true;
            }
         } catch (Throwable ignored) {
         }
      }

      return BuiltInBaritone.isActive();
   }

   private static Object primaryBaritone() throws Exception {
      Class<?> api = Class.forName("baritone.api.BaritoneAPI");
      Object provider = api.getMethod("getProvider").invoke(null);
      return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
   }

   public static boolean executeCommand(String commandLine) {
      if (!externalPresent() || commandLine == null || commandLine.isBlank()) {
         return false;
      }

      try {
         ensureHooked();
         Object baritone = primaryBaritone();
         Object cmdManager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);
         Object res = cmdManager.getClass().getMethod("execute", String.class).invoke(cmdManager, commandLine.trim());
         return !(res instanceof Boolean b) || b;
      } catch (Throwable t) {
         Gemini.LOGGER.debug("External Baritone command fallback: {}", t.toString());
         return false;
      }
   }

   public static boolean gotoBlock(BlockPos pos) {
      if (pos == null) {
         return false;
      }

      if (externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalInterface = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(pos.getX(), pos.getY(), pos.getZ());
            customGoal.getClass().getMethod("setGoalAndPath", goalInterface).invoke(customGoal, goal);
            return true;
         } catch (Throwable ignored) {
         }
      }

      BuiltInBaritone.gotoBlock(pos);
      return true;
   }

   public static boolean gotoXZ(int x, int z) {
      if (externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalInterface = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalXzClass = Class.forName("baritone.api.pathing.goals.GoalXZ");
            Object goal = goalXzClass.getConstructor(int.class, int.class).newInstance(x, z);
            customGoal.getClass().getMethod("setGoalAndPath", goalInterface).invoke(customGoal, goal);
            return true;
         } catch (Throwable ignored) {
         }
      }

      BuiltInBaritone.gotoXZ(x, z);
      return true;
   }

   public static boolean mineBlocks(Set<Block> blocks) {
      if (blocks == null || blocks.isEmpty()) {
         return false;
      }

      if (externalPresent()) {
         String[] names = blocks.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toArray(String[]::new);
         if (mineByNames(names)) {
            return true;
         }
      }

      BuiltInBaritone.mineBlocks(blocks);
      return true;
   }

   public static boolean mineByNames(String[] names) {
      if (names == null || names.length == 0) {
         return false;
      }

      if (externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object mineProcess = baritone.getClass().getMethod("getMineProcess").invoke(baritone);
            mineProcess.getClass().getMethod("mineByName", int.class, String[].class).invoke(mineProcess, 0, names);
            return true;
         } catch (Throwable ignored) {
            if (executeCommand("mine " + String.join(" ", names))) {
               return true;
            }
         }
      }

      return BuiltInBaritone.mineByNames(names);
   }

   public static void follow(Predicate<Entity> filter) {
      if (externalPresent() && filter != null) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object followProcess = baritone.getClass().getMethod("getFollowProcess").invoke(baritone);
            followProcess.getClass().getMethod("follow", Predicate.class).invoke(followProcess, filter);
            return;
         } catch (Throwable ignored) {
         }
      }

      BuiltInBaritone.follow(filter);
   }

   public static void startTunnel(Direction dir, int length) {
      if (externalPresent() && executeCommand("tunnel 2 1 " + Math.max(1, length))) {
         return;
      }

      BuiltInBaritone.startTunnel(dir, length);
   }

   public static void startFarm(int range) {
      if (externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object farmProcess = baritone.getClass().getMethod("getFarmProcess").invoke(baritone);
            farmProcess.getClass().getMethod("farm", int.class).invoke(farmProcess, range);
            return;
         } catch (Throwable ignored) {
            if (executeCommand("farm " + range)) {
               return;
            }
         }
      }

      BuiltInBaritone.startFarm();
   }

   public static void startExplore(BlockPos origin) {
      if (origin != null && externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object exploreProcess = baritone.getClass().getMethod("getExploreProcess").invoke(baritone);
            exploreProcess.getClass().getMethod("explore", int.class, int.class).invoke(exploreProcess, origin.getX(), origin.getZ());
            return;
         } catch (Throwable ignored) {
            if (executeCommand("explore " + origin.getX() + " " + origin.getZ())) {
               return;
            }
         }
      }

      if (origin != null) {
         BuiltInBaritone.startExplore(origin);
      }
   }

   public static void startExcavate(BlockPos cornerA, BlockPos cornerB) {
      if (cornerA != null && cornerB != null && externalPresent()) {
         try {
            ensureHooked();
            Object baritone = primaryBaritone();
            Object builderProcess = baritone.getClass().getMethod("getBuilderProcess").invoke(baritone);
            builderProcess.getClass().getMethod("clearArea", BlockPos.class, BlockPos.class).invoke(builderProcess, cornerA, cornerB);
            return;
         } catch (Throwable ignored) {
         }
      }

      if (cornerA != null && cornerB != null) {
         BuiltInBaritone.startExcavate(cornerA, cornerB);
      }
   }

   public static void stopAll() {
      if (externalPresent()) {
         try {
            Object baritone = primaryBaritone();
            Object behavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            behavior.getClass().getMethod("cancelEverything").invoke(behavior);
         } catch (Throwable ignored) {
            executeCommand("stop");
         }
      }

      BuiltInBaritone.stop();
   }

   public static String statusSummary() {
      if (externalPresent()) {
         try {
            Object baritone = primaryBaritone();
            Object behavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            boolean pathing = (Boolean)behavior.getClass().getMethod("isPathing").invoke(behavior);
            Object goal = behavior.getClass().getMethod("getGoal").invoke(behavior);
            if (pathing || goal != null) {
               return "Baritone API: " + (pathing ? "Pathing" : "Active") + (goal != null ? " -> " + goal.toString() : "");
            }
         } catch (Throwable ignored) {
         }
      }

      return BuiltInBaritone.statusSummary();
   }

   private static void ensureHooked() {
      if (!hooked && externalPresent()) {
         synchronized (BaritoneBridge.class) {
            if (!hooked && externalPresent()) {
               try {
                  registerPauseProcess();
                  hooked = true;
                  Gemini.LOGGER.info("Hooked native BaritoneAPI alongside Built-In Baritone engine.");
               } catch (Throwable var3) {
                  apiAvailable = false;
                  Gemini.LOGGER.info("Using Gemini Built-In Baritone engine.");
               }
            }
         }
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void registerPauseProcess() throws Exception {
      Object baritone = primaryBaritone();
      Object control = baritone.getClass().getMethod("getPathingControlManager").invoke(baritone);
      Class<?> processType = Class.forName("baritone.api.process.IBaritoneProcess");
      Class<?> commandType = Class.forName("baritone.api.process.PathingCommand");
      Class<?> commandKind = Class.forName("baritone.api.process.PathingCommandType");
      Object requestPause = Enum.valueOf((Class<Enum>)commandKind.asSubclass(Enum.class), "REQUEST_PAUSE");
      Constructor<?> commandCtor = commandType.getConstructor(Class.forName("baritone.api.pathing.goals.Goal"), commandKind);
      Object pauseCommand = commandCtor.newInstance(null, requestPause);
      InvocationHandler handler = new BaritoneBridge.CombatPauseHandler(pauseCommand);
      Object process = Proxy.newProxyInstance(processType.getClassLoader(), new Class[]{processType}, handler);
      control.getClass().getMethod("registerProcess", processType).invoke(control, process);
   }

   private static final class CombatPauseHandler implements InvocationHandler {
      private final Object pauseCommand;

      private CombatPauseHandler(Object pauseCommand) {
         this.pauseCommand = pauseCommand;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) {
         String name = method.getName();
         if ("isActive".equals(name)) {
            return Boolean.valueOf(BaritoneBridge.isPaused());
         } else if ("onTick".equals(name)) {
            return this.pauseCommand;
         } else if ("isTemporary".equals(name)) {
            return Boolean.TRUE;
         } else if ("priority".equals(name)) {
            return Double.valueOf(COMBAT_PRIORITY);
         } else if ("displayName".equals(name) || "displayName0".equals(name)) {
            return "Gemini Combat";
         } else if ("onLostControl".equals(name)) {
            return null;
         } else if ("toString".equals(name)) {
            return "GeminiCombatPause";
         } else if ("hashCode".equals(name)) {
            return Integer.valueOf(System.identityHashCode(proxy));
         } else if ("equals".equals(name)) {
            return Boolean.valueOf(args != null && args.length == 1 && args[0] == proxy);
         } else {
            return defaultValue(method.getReturnType());
         }
      }

      private static Object defaultValue(Class<?> type) {
         if (!type.isPrimitive()) {
            return null;
         } else if (type == boolean.class) {
            return Boolean.FALSE;
         } else if (type == double.class) {
            return Double.valueOf(0.0);
         } else if (type == float.class) {
            return Float.valueOf(0.0F);
         } else if (type == long.class) {
            return Long.valueOf(0L);
         } else {
            return Integer.valueOf(0);
         }
      }
   }
}
