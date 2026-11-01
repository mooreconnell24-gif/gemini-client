package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public final class SpCommands {
   private SpCommands() {
   }

   public static boolean run(String command) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (SingleplayerGate.allowed() && server != null && client.player != null) {
         server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
         return true;
      } else {
         return false;
      }
   }
}
