package gemini.util;

import gemini.Gemini;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

/**
 * Sends every pathing request to the real Baritone when it is installed next to Gemini, and to the built-in
 * pathfinder otherwise.
 *
 * <p>Baritone is a separate LGPL mod, so it is not bundled: drop the Baritone jar for this Minecraft version into
 * the mods folder and Gemini picks it up at startup (or on a later probe if it initialises after us). Every call
 * goes through reflection on the public {@code baritone.api} interfaces, so a missing, older or newer Baritone can
 * never break the client: a call that fails simply falls back to the built-in engine for that request.
 */
public final class BaritoneBridge {
   public static final String MOD_ID = "baritone";
   private static final double PAUSE_PRIORITY = 5.0;
   private static final long REPROBE_NANOS = 5_000_000_000L;
   private static final Set<String> PAUSE_REASONS = ConcurrentHashMap.newKeySet();
   private static volatile boolean hooked;
   private static volatile Boolean apiAvailable;
   private static volatile Boolean modLoaded;
   private static volatile boolean probed;
   private static volatile long lastProbe;
   private static Method getPrimary;
   private static Method getPathingBehavior;
   private static Method isPathing;
   private static Method getGoal;
   private static Object provider;

   private BaritoneBridge() {
   }

   /** Whether the Baritone mod is in the mods folder at all (cheap, cached). */
   public static boolean modInstalled() {
      Boolean cached = modLoaded;
      if (cached == null) {
         boolean present;
         try {
            present = FabricLoader.getInstance().getModContainer(MOD_ID).isPresent();
         } catch (Throwable ignored) {
            present = false;
         }

         modLoaded = present;
         return present;
      }

      return cached;
   }

   /** The installed Baritone's version, or null without it. */
   public static String installedVersion() {
      try {
         return FabricLoader.getInstance().getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
      } catch (Throwable ignored) {
         return null;
      }
   }

   /**
    * Whether the real Baritone API answers. A positive answer is cached for good; a mod that is installed but not
    * ready yet is probed again every few seconds, so Baritone initialising after Gemini is still picked up.
    */
   public static boolean externalPresent() {
      Boolean cached = apiAvailable;
      if (cached != null) {
         return cached;
      } else if (!modInstalled()) {
         apiAvailable = false;
         return false;
      }

      long now = System.nanoTime();
      if (probed && now - lastProbe < REPROBE_NANOS) {
         return false;
      }

      probed = true;
      lastProbe = now;

      try {
         Class<?> api = Class.forName("baritone.api.BaritoneAPI", true, BaritoneBridge.class.getClassLoader());
         Object found = api.getMethod("getProvider").invoke(null);
         Class<?> providerType = Class.forName("baritone.api.IBaritoneProvider");
         Method primary = providerType.getMethod("getPrimaryBaritone");
         Object baritone = found == null ? null : primary.invoke(found);
         if (baritone != null) {
            Class<?> baritoneType = Class.forName("baritone.api.IBaritone");
            Class<?> behaviorType = Class.forName("baritone.api.behavior.IPathingBehavior");
            getPathingBehavior = baritoneType.getMethod("getPathingBehavior");
            isPathing = behaviorType.getMethod("isPathing");
            getGoal = behaviorType.getMethod("getGoal");
            getPrimary = primary;
            provider = found;
            apiAvailable = true;
            Gemini.LOGGER.info("Baritone {} found: it now drives the bot modules and # commands.", installedVersion());
            return true;
         }
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone API not ready: {}", t.toString());
      }

      return false;
   }

   public static boolean hasExternalBaritone() {
      return externalPresent();
   }

   public static boolean present() {
      return true;
   }

   public static String engineName() {
      return externalPresent() ? "Baritone" : "Built-in pathfinder";
   }

   /** A longer description for status output: which engine, and how to get the real one when it is missing. */
   public static String engineDetail() {
      if (externalPresent()) {
         return "Baritone " + installedVersion() + " (installed mod)";
      } else if (modInstalled()) {
         return "Baritone is installed but its API has not answered yet; using the built-in pathfinder";
      } else {
         return "built-in A* pathfinder (install the Baritone mod for the full engine)";
      }
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
      if (!Boolean.TRUE.equals(apiAvailable)) {
         apiAvailable = null;
         probed = false;
      }

      ensureHooked();
   }

   public static void tick() {
      BuiltInBaritone.tick();
   }

   public static void render3D() {
      BuiltInBaritone.render3D();
   }

   /** Whether either engine is working on something. */
   public static boolean isActive() {
      return externalPathing() || BuiltInBaritone.isActive();
   }

   /** Whether the real Baritone is currently following a path. */
   public static boolean externalPathing() {
      if (externalPresent()) {
         try {
            Object behavior = getPathingBehavior.invoke(primaryBaritone());
            return (Boolean)isPathing.invoke(behavior);
         } catch (Throwable ignored) {
         }
      }

      return false;
   }

   /** A short word for the HUD ("Pathing", "Mine", ...), or null when nothing is running. */
   public static String taskLabel() {
      if (externalPresent()) {
         try {
            Object behavior = getPathingBehavior.invoke(primaryBaritone());
            if ((Boolean)isPathing.invoke(behavior)) {
               return "Pathing";
            }
         } catch (Throwable ignored) {
         }
      }

      return BuiltInBaritone.isActive() ? BuiltInBaritone.task().label : null;
   }

   private static Object primaryBaritone() throws Exception {
      if (getPrimary != null && provider != null) {
         return getPrimary.invoke(provider);
      }

      Class<?> api = Class.forName("baritone.api.BaritoneAPI");
      Object found = api.getMethod("getProvider").invoke(null);
      return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(found);
   }

   private static Object part(Object baritone, String getter) throws Exception {
      return Class.forName("baritone.api.IBaritone").getMethod(getter).invoke(baritone);
   }

   private static Object call(String iface, Object target, String method, Class<?>[] types, Object... args) throws Exception {
      return Class.forName(iface).getMethod(method, types).invoke(target, args);
   }

   /**
    * Sets a field of Baritone's own settings (renderPath, allowBreak, colorCurrentPath...). Returns false when
    * Baritone is absent or has no such setting; the built-in engine keeps its own copies regardless.
    */
   public static boolean setExternalSetting(String name, Object value) {
      if (!externalPresent() || name == null) {
         return false;
      }

      try {
         Object settings = Class.forName("baritone.api.BaritoneAPI").getMethod("getSettings").invoke(null);
         Object setting = settings.getClass().getField(name).get(settings);
         setting.getClass().getField("value").set(setting, value);
         return true;
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone setting {} not applied: {}", name, t.toString());
         return false;
      }
   }

   public static boolean executeCommand(String commandLine) {
      if (!externalPresent() || commandLine == null || commandLine.isBlank()) {
         return false;
      }

      try {
         ensureHooked();
         Object cmdManager = part(primaryBaritone(), "getCommandManager");
         Object res = call("baritone.api.command.manager.ICommandManager", cmdManager, "execute", new Class<?>[]{String.class}, commandLine.trim());
         return !(res instanceof Boolean b) || b;
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone command fell back to the built-in engine: {}", t.toString());
         return false;
      }
   }

   private static boolean setGoalAndPath(Object goal) throws Exception {
      Object customGoal = part(primaryBaritone(), "getCustomGoalProcess");
      Class<?> goalInterface = Class.forName("baritone.api.pathing.goals.Goal");
      call("baritone.api.process.ICustomGoalProcess", customGoal, "setGoalAndPath", new Class<?>[]{goalInterface}, goal);
      return true;
   }

   public static boolean gotoBlock(BlockPos pos) {
      if (pos == null) {
         return false;
      }

      if (externalPresent()) {
         try {
            ensureHooked();
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(pos.getX(), pos.getY(), pos.getZ());
            if (setGoalAndPath(goal)) {
               return true;
            }
         } catch (Throwable ignored) {
            if (executeCommand("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ())) {
               return true;
            }
         }
      }

      BuiltInBaritone.gotoBlock(pos);
      return true;
   }

   public static boolean gotoXZ(int x, int z) {
      if (externalPresent()) {
         try {
            ensureHooked();
            Class<?> goalXzClass = Class.forName("baritone.api.pathing.goals.GoalXZ");
            Object goal = goalXzClass.getConstructor(int.class, int.class).newInstance(x, z);
            if (setGoalAndPath(goal)) {
               return true;
            }
         } catch (Throwable ignored) {
            if (executeCommand("goto " + x + " " + z)) {
               return true;
            }
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
            Object mineProcess = part(primaryBaritone(), "getMineProcess");
            call("baritone.api.process.IMineProcess", mineProcess, "mineByName", new Class<?>[]{int.class, String[].class}, 0, names);
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
            Object followProcess = part(primaryBaritone(), "getFollowProcess");
            call("baritone.api.process.IFollowProcess", followProcess, "follow", new Class<?>[]{Predicate.class}, filter);
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
            Object farmProcess = part(primaryBaritone(), "getFarmProcess");
            call("baritone.api.process.IFarmProcess", farmProcess, "farm", new Class<?>[]{int.class}, range);
            return;
         } catch (Throwable ignored) {
            if (executeCommand("farm " + range) || executeCommand("farm")) {
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
            Object exploreProcess = part(primaryBaritone(), "getExploreProcess");
            call("baritone.api.process.IExploreProcess", exploreProcess, "explore", new Class<?>[]{int.class, int.class}, origin.getX(), origin.getZ());
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
            Object builderProcess = part(primaryBaritone(), "getBuilderProcess");
            call("baritone.api.process.IBuilderProcess", builderProcess, "clearArea", new Class<?>[]{BlockPos.class, BlockPos.class}, cornerA, cornerB);
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
            Object behavior = getPathingBehavior.invoke(primaryBaritone());
            call("baritone.api.behavior.IPathingBehavior", behavior, "cancelEverything", new Class<?>[0]);
         } catch (Throwable ignored) {
            executeCommand("stop");
         }
      }

      BuiltInBaritone.stop();
   }

   public static String statusSummary() {
      if (externalPresent()) {
         try {
            Object behavior = getPathingBehavior.invoke(primaryBaritone());
            boolean pathing = (Boolean)isPathing.invoke(behavior);
            Object goal = getGoal.invoke(behavior);
            if (pathing || goal != null) {
               String state = isPaused() ? "Paused (" + pauseReason() + ")" : pathing ? "Pathing" : "Goal set";
               return "Baritone: " + state + (goal != null ? " -> " + goal : "");
            } else if (!BuiltInBaritone.isActive()) {
               return "Baritone: Idle";
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
                  Gemini.LOGGER.info("Registered Gemini's pause process with Baritone (combat and manual pauses stop it mid-path).");
               } catch (Throwable t) {
                  hooked = true;
                  Gemini.LOGGER.info("Baritone pause hook unavailable ({}); #pause only pauses the built-in engine.", t.toString());
               }
            }
         }
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static void registerPauseProcess() throws Exception {
      Object baritone = primaryBaritone();
      Object control = part(baritone, "getPathingControlManager");
      Class<?> processType = Class.forName("baritone.api.process.IBaritoneProcess");
      Class<?> commandType = Class.forName("baritone.api.process.PathingCommand");
      Class<?> commandKind = Class.forName("baritone.api.process.PathingCommandType");
      Object requestPause = Enum.valueOf((Class<Enum>)commandKind.asSubclass(Enum.class), "REQUEST_PAUSE");
      Constructor<?> commandCtor = commandType.getConstructor(Class.forName("baritone.api.pathing.goals.Goal"), commandKind);
      Object pauseCommand = commandCtor.newInstance(null, requestPause);
      InvocationHandler handler = new BaritoneBridge.CombatPauseHandler(pauseCommand);
      Object process = Proxy.newProxyInstance(processType.getClassLoader(), new Class[]{processType}, handler);
      call("baritone.api.pathing.calc.IPathingControlManager", control, "registerProcess", new Class<?>[]{processType}, process);
   }

   /**
    * A temporary Baritone process that is active while any pause reason is set and answers every tick with
    * REQUEST_PAUSE. Baritone's control manager asks processes in priority order and lets a temporary one hold the
    * tick without cancelling the others, so the mine/goto/follow task resumes as soon as the reason clears.
    */
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
            return Double.valueOf(PAUSE_PRIORITY);
         } else if ("displayName".equals(name) || "displayName0".equals(name)) {
            return "Gemini Pause";
         } else if ("onLostControl".equals(name)) {
            return null;
         } else if ("toString".equals(name)) {
            return "GeminiPause";
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
