package gemini.util;

import gemini.Gemini;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneBridge {
   public static final String MOD_ID = "baritone";
   private static final double COMBAT_PRIORITY = 5.0;
   private static final Set<String> PAUSE_REASONS = ConcurrentHashMap.newKeySet();
   private static volatile boolean hooked;
   private static volatile boolean unavailable;

   private BaritoneBridge() {
   }

   public static boolean externalPresent() {
      return !unavailable && FabricLoader.getInstance().isModLoaded("baritone");
   }

   public static boolean present() {
      return true;
   }

   public static String engineName() {
      return externalPresent() ? "Baritone + Built-In Engine" : "Built-In Baritone Engine";
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
      ensureHooked();
   }

   public static void tick() {
      BuiltInBaritone.tick();
   }

   public static void render3D() {
      BuiltInBaritone.render3D();
   }

   private static void ensureHooked() {
      if (!hooked && !unavailable && FabricLoader.getInstance().isModLoaded("baritone")) {
         synchronized (BaritoneBridge.class) {
            if (!hooked && !unavailable) {
               try {
                  registerPauseProcess();
                  hooked = true;
                  Gemini.LOGGER.info("External Baritone hooked alongside Built-In Baritone engine.");
               } catch (Throwable var3) {
                  unavailable = true;
                  Gemini.LOGGER.info("Using Gemini Built-In Baritone engine.");
               }
            }
         }
      }
   }

   private static void registerPauseProcess() throws Exception {
      Class<?> api = Class.forName("baritone.api.BaritoneAPI");
      Object provider = api.getMethod("getProvider").invoke(null);
      Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
      Object control = baritone.getClass().getMethod("getPathingControlManager").invoke(baritone);
      Class<?> processType = Class.forName("baritone.api.process.IBaritoneProcess");
      Class<?> commandType = Class.forName("baritone.api.process.PathingCommand");
      Class<?> commandKind = Class.forName("baritone.api.process.PathingCommandType");
      Object requestPause = Enum.valueOf(commandKind.asSubclass(Enum.class), "REQUEST_PAUSE");
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
         switch (name) {
            case "isActive":
               return isPaused();
            case "onTick":
               return this.pauseCommand;
            case "isTemporary":
               return Boolean.TRUE;
            case "priority":
               return COMBAT_PRIORITY;
            case "displayName":
            case "displayName0":
               return "Gemini Combat";
            case "onLostControl":
               return null;
            case "toString":
            case "hashCode":
            case "equals":
               return handleObject(proxy, method, args);
            default:
               return defaultValue(method.getReturnType());
         }
      }

      private static Object handleObject(Object proxy, Method method, Object[] args) {
         String name = method.getName();
         switch (name) {
            case "toString":
               return "GeminiCombatPause";
            case "hashCode":
               return System.identityHashCode(proxy);
            case "equals":
               return args != null && args.length == 1 && args[0] == proxy;
            default:
               return null;
         }
      }

      private static Object defaultValue(Class<?> type) {
         if (!type.isPrimitive()) {
            return null;
         } else if (type == boolean.class) {
            return false;
         } else if (type == double.class) {
            return 0.0;
         } else if (type == float.class) {
            return 0.0F;
         } else if (type == long.class) {
            return 0L;
         } else if (type == int.class) {
            return 0;
         } else if (type == short.class) {
            return (short)0;
         } else if (type == byte.class) {
            return (byte)0;
         } else if (type == char.class) {
            return (char)0;
         } else {
            return null;
         }
      }
   }
}
