package gemini.util;

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
}
