package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public final class SingleplayerGate {
   private static boolean allowEverywhere = true;

   private SingleplayerGate() {
   }

   public static boolean allowed() {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.level != null ? allowEverywhere || isPrivateSingleplayer(client) : false;
   }

   public static void setAllowEverywhere(boolean allow) {
      allowEverywhere = allow;
   }

   public static boolean isAllowEverywhere() {
      return allowEverywhere;
   }

   public static boolean isPrivateSingleplayer(Minecraft client) {
      if (!client.hasSingleplayerServer()) {
         return false;
      } else {
         IntegratedServer server = client.getSingleplayerServer();
         return server != null && !server.isPublished();
      }
   }

   public static boolean singleplayerOnly() {
      return Minecraft.getInstance().hasSingleplayerServer();
   }

   public static String denialMessage() {
      Minecraft client = Minecraft.getInstance();
      return client.player != null && client.level != null
         ? "§bGemini §7requires an integrated single-player server for this command."
         : "§bGemini §7needs a world loaded.";
   }
}
