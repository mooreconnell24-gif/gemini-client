package gemini.util;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

public final class SpCommands {
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
               CommandSourceStack source = player != null
                  ? player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER).withSuppressedOutput()
                  : server.createCommandSourceStack().withSuppressedOutput();
               server.getCommands().performPrefixedCommand(source, command);
            }
         );
         return true;
      } else {
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
