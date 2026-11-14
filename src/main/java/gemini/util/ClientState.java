package gemini.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The checks nearly every module makes before it acts. These used to be copied into each category's helper class
 * (four identical {@code ready} methods); they live here once.
 */
public final class ClientState {
   private ClientState() {
   }

   /** A living, non-spectating player in a loaded world with a game mode and a connection to act through. */
   public static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && client.level != null
         && client.gameMode != null
         && client.getConnection() != null
         && !player.isSpectator()
         && !player.isDeadOrDying();
   }

   /** {@link #ready} with no screen open: for modules that use items or place blocks on the player's behalf. */
   public static boolean readyNoScreen(Minecraft client) {
      return client.gui.screen() == null && ready(client);
   }
}
