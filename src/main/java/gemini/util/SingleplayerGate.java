package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * Keeps Gemini to private single-player worlds. Server connections are refused outright by the
 * mixins in {@code gemini.mixin.lock}; this class is the second line of defence (it drops any
 * remote level that still appears) and decides whether modules may run right now.
 */
public final class SingleplayerGate {
   public static final String MULTIPLAYER_BLOCKED = "Gemini Client only runs in single-player worlds, so server connections are blocked while it is installed. Remove the Gemini jar from your mods folder to play on servers.";

   private SingleplayerGate() {
   }

   public static void tick(Minecraft client) {
      if (client != null && client.level != null && !client.hasSingleplayerServer()) {
         client.level.disconnect(Component.literal(MULTIPLAYER_BLOCKED));
      }
   }

   /** True when a private (not opened to LAN) single-player world is loaded. */
   public static boolean allowed() {
      Minecraft client = Minecraft.getInstance();
      return client != null && client.player != null && client.level != null && isPrivateSingleplayer(client);
   }

   public static boolean isPrivateSingleplayer(Minecraft client) {
      if (!client.hasSingleplayerServer()) {
         return false;
      }

      IntegratedServer server = client.getSingleplayerServer();
      return server != null && !server.isPublished();
   }

   public static boolean singleplayerOnly() {
      Minecraft client = Minecraft.getInstance();
      return client != null && client.hasSingleplayerServer();
   }

   public static String denialMessage() {
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.player == null || client.level == null) {
         return "§bGemini Client §7needs a single-player world loaded.";
      }

      return client.hasSingleplayerServer()
         ? "§bGemini Client §7is paused while this world is open to other players."
         : "§bGemini Client §7only runs in single-player worlds.";
   }
}
