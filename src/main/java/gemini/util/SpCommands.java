package gemini.util;

import gemini.Gemini;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

public final class SpCommands {
   private static final int MAX_FAILURES = 64;
   private static final ArrayDeque<String> FAILURES = new ArrayDeque<>();

   private SpCommands() {
   }

   public static boolean run(String command) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (SingleplayerGate.allowed() && server != null && client.player != null) {
         UUID self = client.player.getUUID();
         server.execute(
            () -> {
               ServerPlayer player = server.getPlayerList().getPlayer(self);
               CommandSourceStack base = player != null
                  ? player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER)
                  : server.createCommandSourceStack();
               server.getCommands().performPrefixedCommand(base.withSource(new FailureRecorder(command)), command);
            }
         );
         return true;
      } else {
         return false;
      }
   }

   /**
    * Commands Gemini issued that the server rejected, oldest first, as "command -> reason"; reading clears them.
    * Success output is discarded as before. Failures used to be discarded too, which hid commands whose syntax no
    * longer matched the game. They are kept (a bounded number) for the log, {@code .version} and the game tests.
    */
   public static List<String> drainFailures() {
      synchronized (FAILURES) {
         List<String> copy = new ArrayList<>(FAILURES);
         FAILURES.clear();
         return copy;
      }
   }

   /** Receives what the server would have written to chat for one command and keeps only the failures. */
   private static final class FailureRecorder implements CommandSource {
      private final String command;

      private FailureRecorder(String command) {
         this.command = command;
      }

      @Override
      public void sendSystemMessage(Component message) {
         String reason = message.getString();
         Gemini.LOGGER.debug("Command /{} failed: {}", this.command, reason);
         synchronized (FAILURES) {
            if (FAILURES.size() >= MAX_FAILURES) {
               FAILURES.removeFirst();
            }

            FAILURES.addLast(this.command + " -> " + reason);
         }
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
}
