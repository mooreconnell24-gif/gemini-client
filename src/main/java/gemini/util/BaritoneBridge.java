package gemini.util;

import gemini.Gemini;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

/**
 * Sends every pathing request to the Baritone bundled with Gemini (or one installed next to it), and to the built-in
 * pathfinder otherwise.
 *
 * <p><b>No hard dependency.</b> Baritone is only ever reached through its public {@code baritone.api} interfaces,
 * looked up by name, so a missing, older or newer Baritone can never break the client. The lookups happen once:
 * {@link Api} resolves every entry point into a {@link MethodHandle} when Baritone first answers, and each call
 * after that is a direct handle invocation. An entry point a given Baritone build lacks is simply absent, and the
 * request falls back to a Baritone command and then to the built-in engine.
 *
 * <p><b>One engine at a time.</b> Both engines steer the player, so only one may hold a task. Whichever engine a
 * request lands on becomes the {@link Engine owner} and the other is stopped first. That covers the awkward
 * moments: Baritone finishing its start-up while the built-in engine is already walking, a Baritone call failing
 * half-way, and commands that start the built-in engine directly.
 *
 * <p><b>Cheap status.</b> Whether Baritone is pathing is asked once per tick and remembered, so the HUD, the status
 * line and the pause logic never call into Baritone per frame.
 */
public final class BaritoneBridge {
   public static final String MOD_ID = "baritone";
   /** Mod ids Baritone builds register under: upstream uses "baritone", the 26.3 fork bundled with Gemini uses "baritone-fork". */
   private static final String[] MOD_IDS = {MOD_ID, "baritone-fork"};
   private static final double PAUSE_PRIORITY = 5.0;
   private static final long REPROBE_NANOS = 5_000_000_000L;
   private static final Set<String> PAUSE_REASONS = ConcurrentHashMap.newKeySet();
   private static volatile boolean hooked;
   private static volatile Boolean modLoaded;
   private static volatile boolean probed;
   private static volatile long lastProbe;
   /** Baritone's entry points once it has answered; null until then. */
   private static volatile Api api;
   private static Engine owner = Engine.NONE;
   private static boolean builtInWasActive;
   private static boolean pathingNow;
   private static Object goalNow;
   /** The chat-control state last pushed to Baritone; null until Baritone has answered once. */
   private static Boolean chatControlApplied;
   private static Boolean savedChatControl;
   private static Boolean savedPrefixControl;
   private static Boolean savedChatControlAnyway;

   private BaritoneBridge() {
   }

   /** Which engine currently holds the player. */
   public enum Engine {
      NONE,
      EXTERNAL,
      BUILT_IN
   }

   private static Optional<ModContainer> container() {
      for (String id : MOD_IDS) {
         Optional<ModContainer> found = FabricLoader.getInstance().getModContainer(id);
         if (found.isPresent()) {
            return found;
         }
      }

      return Optional.empty();
   }

   /** Whether the Baritone mod is in the mods folder at all (cheap, cached). */
   public static boolean modInstalled() {
      Boolean cached = modLoaded;
      if (cached == null) {
         boolean present;
         try {
            present = container().isPresent();
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
         return container().map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
      } catch (Throwable ignored) {
         return null;
      }
   }

   /**
    * Whether the real Baritone API answers. A positive answer is kept for good; a mod that is installed but not
    * ready yet is probed again every few seconds, so Baritone initialising after Gemini is still picked up.
    */
   public static boolean externalPresent() {
      if (api != null) {
         return true;
      } else if (!modInstalled()) {
         return false;
      }

      long now = System.nanoTime();
      if (probed && now - lastProbe < REPROBE_NANOS) {
         return false;
      }

      probed = true;
      lastProbe = now;

      try {
         Api resolved = Api.resolve();
         if (resolved != null) {
            api = resolved;
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

   public static Engine owner() {
      return owner;
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
      if (api == null) {
         probed = false;
      }

      ensureHooked();
   }

   /**
    * Once per client tick: refreshes the remembered Baritone state, settles which engine owns the player, and
    * runs the built-in engine.
    */
   public static void tick() {
      Api current = api;
      pathingNow = false;
      goalNow = null;
      if (current != null) {
         try {
            Object behavior = current.pathingBehavior();
            pathingNow = (boolean)current.isPathing.invoke(behavior);
            goalNow = current.getGoal.invoke(behavior);
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone status poll failed: {}", t.toString());
         }
      }

      boolean builtInActive = BuiltInBaritone.isActive();
      if (builtInActive && !builtInWasActive && owner == Engine.EXTERNAL) {
         // A command started the built-in engine directly while Baritone held a task: the newer request wins.
         cancelExternal();
         owner = Engine.BUILT_IN;
      } else if (builtInActive && owner == Engine.EXTERNAL) {
         BuiltInBaritone.stop();
         builtInActive = false;
      } else if (builtInActive) {
         owner = Engine.BUILT_IN;
      } else if (owner == Engine.BUILT_IN || owner == Engine.EXTERNAL && !pathingNow && goalNow == null) {
         owner = Engine.NONE;
      }

      builtInWasActive = builtInActive;
      BuiltInBaritone.tick();
   }

   public static void render3D() {
      BuiltInBaritone.render3D();
   }

   /** Whether either engine is working on something. */
   public static boolean isActive() {
      return pathingNow || BuiltInBaritone.isActive();
   }

   /** Whether the real Baritone was following a path at the last tick. */
   public static boolean externalPathing() {
      return pathingNow;
   }

   /** A short word for the HUD ("Pathing", "Mine", ...), or null when nothing is running. */
   public static String taskLabel() {
      if (pathingNow) {
         return "Pathing";
      }

      return BuiltInBaritone.isActive() ? BuiltInBaritone.task().label : null;
   }

   /** Baritone took the request: it owns the player now and the built-in engine lets go. */
   private static boolean handedToExternal() {
      if (BuiltInBaritone.isActive()) {
         BuiltInBaritone.stop();
      }

      builtInWasActive = false;
      owner = Engine.EXTERNAL;
      return true;
   }

   /** The built-in engine is about to take the request: make sure Baritone is not still walking an older one. */
   private static void handToBuiltIn() {
      if (owner == Engine.EXTERNAL || pathingNow) {
         cancelExternal();
      }

      owner = Engine.BUILT_IN;
      builtInWasActive = true;
   }

   private static void cancelExternal() {
      Api current = api;
      if (current == null) {
         return;
      }

      try {
         current.cancelEverything.invoke(current.pathingBehavior());
      } catch (Throwable t) {
         executeRaw(current, "stop");
      }

      pathingNow = false;
      goalNow = null;
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
         Object setting = api.setting(name);
         setting.getClass().getField("value").set(setting, value);
         return true;
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone setting {} not applied: {}", name, t.toString());
         return false;
      }
   }

   private static Object externalSetting(String name) {
      try {
         Object setting = api.setting(name);
         return setting.getClass().getField("value").get(setting);
      } catch (Throwable t) {
         return null;
      }
   }

   /**
    * Baritone listens to chat on its own. While Gemini may not act (switched off, a world open to LAN, any server)
    * its chat control is turned off as well and whatever it was doing is cancelled, so the bundled bot is exactly
    * as inert as the rest of the client. The player's own values are put back when Gemini may act again.
    */
   public static void syncChatControl(boolean allowed) {
      // Level-triggered on purpose: Baritone may finish starting up long after the state was first decided.
      if (!externalPresent() || chatControlApplied != null && chatControlApplied == allowed) {
         return;
      }

      chatControlApplied = allowed;
      if (allowed) {
         if (savedChatControl != null) {
            setExternalSetting("chatControl", savedChatControl);
            setExternalSetting("prefixControl", savedPrefixControl == null ? Boolean.TRUE : savedPrefixControl);
            setExternalSetting("chatControlAnyway", savedChatControlAnyway == null ? Boolean.FALSE : savedChatControlAnyway);
            savedChatControl = null;
            savedPrefixControl = null;
            savedChatControlAnyway = null;
         }
      } else {
         if (savedChatControl == null) {
            savedChatControl = externalSetting("chatControl") instanceof Boolean b ? b : Boolean.TRUE;
            savedPrefixControl = externalSetting("prefixControl") instanceof Boolean b ? b : Boolean.TRUE;
            savedChatControlAnyway = externalSetting("chatControlAnyway") instanceof Boolean b ? b : Boolean.FALSE;
         }

         setExternalSetting("chatControl", Boolean.FALSE);
         setExternalSetting("prefixControl", Boolean.FALSE);
         setExternalSetting("chatControlAnyway", Boolean.FALSE);
         cancelExternal();
      }
   }

   /** Runs a Baritone command. The command decides what Baritone does next, so it takes ownership when it succeeds. */
   public static boolean executeCommand(String commandLine) {
      if (!externalPresent() || commandLine == null || commandLine.isBlank()) {
         return false;
      }

      ensureHooked();
      if (executeRaw(api, commandLine.trim())) {
         String verb = commandLine.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
         if (!"stop".equals(verb) && !"cancel".equals(verb) && !"help".equals(verb) && !"set".equals(verb)) {
            handedToExternal();
         }

         return true;
      }

      return false;
   }

   private static boolean executeRaw(Api current, String commandLine) {
      if (current.execute == null) {
         return false;
      }

      try {
         Object result = current.execute.invoke(current.getCommandManager.invoke(current.baritone()), commandLine);
         return !(result instanceof Boolean ok) || ok;
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone command fell back to the built-in engine: {}", t.toString());
         return false;
      }
   }

   private static boolean setGoalAndPath(Api current, Object goal) throws Throwable {
      if (current.setGoalAndPath == null || goal == null) {
         return false;
      }

      current.setGoalAndPath.invoke(current.getCustomGoalProcess.invoke(current.baritone()), goal);
      return true;
   }

   public static boolean gotoBlock(BlockPos pos) {
      if (pos == null) {
         return false;
      }

      if (externalPresent()) {
         Api current = api;
         ensureHooked();
         try {
            if (current.newGoalBlock != null && setGoalAndPath(current, current.newGoalBlock.invoke(pos.getX(), pos.getY(), pos.getZ()))) {
               return handedToExternal();
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone goto failed, trying its command: {}", t.toString());
         }

         if (executeCommand("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ())) {
            return true;
         }
      }

      handToBuiltIn();
      BuiltInBaritone.gotoBlock(pos);
      return true;
   }

   public static boolean gotoXZ(int x, int z) {
      if (externalPresent()) {
         Api current = api;
         ensureHooked();
         try {
            if (current.newGoalXZ != null && setGoalAndPath(current, current.newGoalXZ.invoke(x, z))) {
               return handedToExternal();
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone goto failed, trying its command: {}", t.toString());
         }

         if (executeCommand("goto " + x + " " + z)) {
            return true;
         }
      }

      handToBuiltIn();
      BuiltInBaritone.gotoXZ(x, z);
      return true;
   }

   public static boolean mineBlocks(Set<Block> blocks) {
      if (blocks == null || blocks.isEmpty()) {
         return false;
      }

      if (externalPresent()) {
         String[] names = blocks.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toArray(String[]::new);
         if (mineExternal(names)) {
            return true;
         }
      }

      handToBuiltIn();
      BuiltInBaritone.mineBlocks(blocks);
      return true;
   }

   public static boolean mineByNames(String[] names) {
      if (names == null || names.length == 0) {
         return false;
      }

      if (externalPresent() && mineExternal(names)) {
         return true;
      }

      handToBuiltIn();
      boolean started = BuiltInBaritone.mineByNames(names);
      if (!started) {
         owner = Engine.NONE;
         builtInWasActive = false;
      }

      return started;
   }

   private static boolean mineExternal(String[] names) {
      Api current = api;
      ensureHooked();
      try {
         if (current.mineByName != null) {
            current.mineByName.invoke(current.getMineProcess.invoke(current.baritone()), 0, names);
            return handedToExternal();
         }
      } catch (Throwable t) {
         Gemini.LOGGER.debug("Baritone mine failed, trying its command: {}", t.toString());
      }

      return executeCommand("mine " + String.join(" ", names));
   }

   public static void follow(Predicate<Entity> filter) {
      if (externalPresent() && filter != null) {
         Api current = api;
         ensureHooked();
         try {
            if (current.follow != null) {
               current.follow.invoke(current.getFollowProcess.invoke(current.baritone()), filter);
               handedToExternal();
               return;
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone follow failed, using the built-in engine: {}", t.toString());
         }
      }

      handToBuiltIn();
      BuiltInBaritone.follow(filter);
   }

   public static void startTunnel(Direction dir, int length) {
      if (externalPresent() && executeCommand("tunnel 2 1 " + Math.max(1, length))) {
         return;
      }

      handToBuiltIn();
      BuiltInBaritone.startTunnel(dir, length);
   }

   public static void startFarm(int range) {
      if (externalPresent()) {
         Api current = api;
         ensureHooked();
         try {
            if (current.farm != null) {
               current.farm.invoke(current.getFarmProcess.invoke(current.baritone()), range);
               handedToExternal();
               return;
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone farm failed, trying its command: {}", t.toString());
         }

         if (executeCommand("farm " + range) || executeCommand("farm")) {
            return;
         }
      }

      handToBuiltIn();
      BuiltInBaritone.startFarm();
   }

   public static void startExplore(BlockPos origin) {
      if (origin == null) {
         return;
      }

      if (externalPresent()) {
         Api current = api;
         ensureHooked();
         try {
            if (current.explore != null) {
               current.explore.invoke(current.getExploreProcess.invoke(current.baritone()), origin.getX(), origin.getZ());
               handedToExternal();
               return;
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone explore failed, trying its command: {}", t.toString());
         }

         if (executeCommand("explore " + origin.getX() + " " + origin.getZ())) {
            return;
         }
      }

      handToBuiltIn();
      BuiltInBaritone.startExplore(origin);
   }

   public static void startExcavate(BlockPos cornerA, BlockPos cornerB) {
      if (cornerA == null || cornerB == null) {
         return;
      }

      if (externalPresent()) {
         Api current = api;
         ensureHooked();
         try {
            if (current.clearArea != null) {
               current.clearArea.invoke(current.getBuilderProcess.invoke(current.baritone()), cornerA, cornerB);
               handedToExternal();
               return;
            }
         } catch (Throwable t) {
            Gemini.LOGGER.debug("Baritone clear-area failed, using the built-in engine: {}", t.toString());
         }
      }

      handToBuiltIn();
      BuiltInBaritone.startExcavate(cornerA, cornerB);
   }

   public static void stopAll() {
      cancelExternal();
      BuiltInBaritone.stop();
      owner = Engine.NONE;
      builtInWasActive = false;
   }

   public static String statusSummary() {
      if (api != null && (pathingNow || goalNow != null)) {
         String state = isPaused() ? "Paused (" + pauseReason() + ")" : pathingNow ? "Pathing" : "Goal set";
         return "Baritone: " + state + (goalNow != null ? " -> " + goalNow : "");
      } else if (api != null && !BuiltInBaritone.isActive()) {
         return "Baritone: Idle";
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
   private static void registerPauseProcess() throws Throwable {
      Api current = api;
      Class<?> baritoneType = Class.forName("baritone.api.IBaritone");
      Object control = baritoneType.getMethod("getPathingControlManager").invoke(current.baritone());
      Class<?> processType = Class.forName("baritone.api.process.IBaritoneProcess");
      Class<?> commandType = Class.forName("baritone.api.process.PathingCommand");
      Class<?> commandKind = Class.forName("baritone.api.process.PathingCommandType");
      Object requestPause = Enum.valueOf((Class<Enum>)commandKind.asSubclass(Enum.class), "REQUEST_PAUSE");
      Constructor<?> commandCtor = commandType.getConstructor(Class.forName("baritone.api.pathing.goals.Goal"), commandKind);
      Object pauseCommand = commandCtor.newInstance(null, requestPause);
      InvocationHandler handler = new BaritoneBridge.CombatPauseHandler(pauseCommand);
      Object process = Proxy.newProxyInstance(processType.getClassLoader(), new Class[]{processType}, handler);
      Class.forName("baritone.api.pathing.calc.IPathingControlManager").getMethod("registerProcess", processType).invoke(control, process);
   }

   /**
    * Baritone's public API as method handles, resolved once. {@link #resolve()} returns null while Baritone has no
    * primary instance yet. The status handles are required; every task entry point is optional and left null when
    * this Baritone build does not have it.
    */
   private static final class Api {
      private final Object provider;
      private final Object settings;
      private final MethodHandle getPrimaryBaritone;
      private final MethodHandle getPathingBehavior;
      final MethodHandle isPathing;
      final MethodHandle getGoal;
      final MethodHandle cancelEverything;
      final MethodHandle getCommandManager;
      final MethodHandle execute;
      final MethodHandle getCustomGoalProcess;
      final MethodHandle setGoalAndPath;
      final MethodHandle newGoalBlock;
      final MethodHandle newGoalXZ;
      final MethodHandle getMineProcess;
      final MethodHandle mineByName;
      final MethodHandle getFollowProcess;
      final MethodHandle follow;
      final MethodHandle getFarmProcess;
      final MethodHandle farm;
      final MethodHandle getExploreProcess;
      final MethodHandle explore;
      final MethodHandle getBuilderProcess;
      final MethodHandle clearArea;

      private Api(Object provider, Object settings) throws ReflectiveOperationException {
         MethodHandles.Lookup lookup = MethodHandles.publicLookup();
         Class<?> baritone = Class.forName("baritone.api.IBaritone");
         Class<?> behavior = Class.forName("baritone.api.behavior.IPathingBehavior");
         Class<?> goal = Class.forName("baritone.api.pathing.goals.Goal");
         this.provider = provider;
         this.settings = settings;
         this.getPrimaryBaritone = lookup.unreflect(Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone"));
         this.getPathingBehavior = lookup.unreflect(baritone.getMethod("getPathingBehavior"));
         this.isPathing = lookup.unreflect(behavior.getMethod("isPathing"));
         this.getGoal = lookup.unreflect(behavior.getMethod("getGoal"));
         this.cancelEverything = lookup.unreflect(behavior.getMethod("cancelEverything"));
         this.getCommandManager = optional(lookup, baritone, "getCommandManager");
         this.execute = this.getCommandManager == null ? null : optional(lookup, "baritone.api.command.manager.ICommandManager", "execute", String.class);
         this.getCustomGoalProcess = optional(lookup, baritone, "getCustomGoalProcess");
         this.setGoalAndPath = this.getCustomGoalProcess == null ? null : optional(lookup, "baritone.api.process.ICustomGoalProcess", "setGoalAndPath", goal);
         this.newGoalBlock = constructor(lookup, "baritone.api.pathing.goals.GoalBlock", int.class, int.class, int.class);
         this.newGoalXZ = constructor(lookup, "baritone.api.pathing.goals.GoalXZ", int.class, int.class);
         this.getMineProcess = optional(lookup, baritone, "getMineProcess");
         this.mineByName = this.getMineProcess == null ? null : optional(lookup, "baritone.api.process.IMineProcess", "mineByName", int.class, String[].class);
         this.getFollowProcess = optional(lookup, baritone, "getFollowProcess");
         this.follow = this.getFollowProcess == null ? null : optional(lookup, "baritone.api.process.IFollowProcess", "follow", Predicate.class);
         this.getFarmProcess = optional(lookup, baritone, "getFarmProcess");
         this.farm = this.getFarmProcess == null ? null : optional(lookup, "baritone.api.process.IFarmProcess", "farm", int.class);
         this.getExploreProcess = optional(lookup, baritone, "getExploreProcess");
         this.explore = this.getExploreProcess == null ? null : optional(lookup, "baritone.api.process.IExploreProcess", "explore", int.class, int.class);
         this.getBuilderProcess = optional(lookup, baritone, "getBuilderProcess");
         this.clearArea = this.getBuilderProcess == null
            ? null
            : optional(lookup, "baritone.api.process.IBuilderProcess", "clearArea", BlockPos.class, BlockPos.class);
      }

      static Api resolve() throws Throwable {
         Class<?> entry = Class.forName("baritone.api.BaritoneAPI", true, BaritoneBridge.class.getClassLoader());
         Object provider = entry.getMethod("getProvider").invoke(null);
         if (provider == null) {
            return null;
         }

         Object settings = null;
         try {
            settings = entry.getMethod("getSettings").invoke(null);
         } catch (ReflectiveOperationException ignored) {
            // Settings are optional; the bridge works without them.
         }

         Api resolved = new Api(provider, settings);
         return resolved.baritone() == null ? null : resolved;
      }

      Object baritone() throws Throwable {
         return this.getPrimaryBaritone.invoke(this.provider);
      }

      Object pathingBehavior() throws Throwable {
         return this.getPathingBehavior.invoke(this.baritone());
      }

      Object setting(String name) throws ReflectiveOperationException {
         if (this.settings == null) {
            throw new NoSuchFieldException(name);
         }

         Field field = this.settings.getClass().getField(name);
         return field.get(this.settings);
      }

      private static MethodHandle optional(MethodHandles.Lookup lookup, Class<?> owner, String name, Class<?>... parameters) {
         try {
            Method method = owner.getMethod(name, parameters);
            return lookup.unreflect(method);
         } catch (ReflectiveOperationException | LinkageError e) {
            return null;
         }
      }

      private static MethodHandle optional(MethodHandles.Lookup lookup, String owner, String name, Class<?>... parameters) {
         try {
            return optional(lookup, Class.forName(owner), name, parameters);
         } catch (ReflectiveOperationException | LinkageError e) {
            return null;
         }
      }

      private static MethodHandle constructor(MethodHandles.Lookup lookup, String owner, Class<?>... parameters) {
         try {
            return lookup.unreflectConstructor(Class.forName(owner).getConstructor(parameters));
         } catch (ReflectiveOperationException | LinkageError e) {
            return null;
         }
      }
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
