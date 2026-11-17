package gemini.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs vanilla commands on the integrated server as the local player (owner permission). Success output is
 * suppressed so modules do not spam chat; failures (unknown command, bad argument, "no entity was found") are
 * written to the log once per distinct command and message, and kept in {@link #recentFailures()} so a command a
 * new Minecraft version no longer accepts does not fail silently.
 */
public final class SpCommands {
   private static final Logger LOGGER = LoggerFactory.getLogger("Gemini Commands");
   private static final int KEEP_FAILURES = 64;
   private static final Deque<String> FAILURES = new ArrayDeque<>();
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

   private SpCommands() {
   }

   public static boolean run(String command) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (SingleplayerGate.allowed() && server != null && client.player != null) {
         UUID self = client.player.getUUID();
         server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(self);
            CommandSourceStack base = player != null ? player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER) : server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(base.withSource(new FailureSink(command)), command);
         });
         return true;
      } else {
         return false;
      }
   }

   /**
    * Runs {@code task} on the integrated server thread with the local player's server-side entity. For things no
    * command does (saving the world) or where going through the command parser is pointless.
    */
   public static boolean onServer(Consumer<ServerPlayer> task) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (SingleplayerGate.allowed() && server != null && client.player != null) {
         UUID self = client.player.getUUID();
         server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(self);
            if (player != null) {
               task.accept(player);
            }
         });
         return true;
      } else {
         return false;
      }
   }

   /** Saves the integrated world the way {@code /save-all} does on a dedicated server (that command does not exist in single player). */
   public static boolean saveWorld(boolean flush) {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      return onServer(player -> {
         if (!server.saveEverything(true, flush, true)) {
            failed("save the world", "the server reported that saving failed");
         }
      });
   }

   /** The last failed commands, oldest first, as "command -> message". */
   public static List<String> recentFailures() {
      synchronized (FAILURES) {
         return new ArrayList<>(FAILURES);
      }
   }

   public static void clearFailures() {
      synchronized (FAILURES) {
         FAILURES.clear();
      }

      LOGGED.clear();
   }

   static void failed(String command, String message) {
      String line = command + " -> " + message;
      synchronized (FAILURES) {
         if (FAILURES.size() >= KEEP_FAILURES) {
            FAILURES.removeFirst();
         }

         FAILURES.addLast(line);
      }

      if (LOGGED.add(line)) {
         LOGGER.warn("Command failed: /{}: {}", command, message);
      }
   }

   public static boolean teleport(String dimension, double x, double y, double z) {
      return dimension != null && dimension.matches("[a-z0-9_.:\\-]+")
         ? SpCommands.run(String.format(Locale.ROOT, "execute in %s run tp @s %.3f %.3f %.3f", dimension, x, y, z))
         : false;
   }

   public static boolean gamemode(String mode) {
      String clean = mode == null ? "" : mode.toLowerCase(Locale.ROOT);

      return switch (clean) {
         case "0", "s", "survival" -> SpCommands.run("gamemode survival");
         case "1", "c", "creative" -> SpCommands.run("gamemode creative");
         case "2", "a", "adventure" -> SpCommands.run("gamemode adventure");
         case "3", "sp", "spectator" -> SpCommands.run("gamemode spectator");
         default -> false;
      };
   }

   /** Swallows success output and records failure messages for one command. */
   private record FailureSink(String command) implements CommandSource {
      @Override
      public void sendSystemMessage(Component message) {
         SpCommands.failed(this.command, message.getString());
      }

      @Override
      public boolean acceptsSuccess() {
         return false;
      }

      @Override
      public boolean acceptsFailure() {
         return true;
      }

      @Override
      public boolean shouldInformAdmins() {
         return false;
      }
   }
}
