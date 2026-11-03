package gemini.util;

import gemini.Gemini;
import gemini.module.modules.misc.InfernoBypass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

/**
 * Outgoing packet handling shared by Gemini's two hooks on {@code ClientCommonPacketListenerImpl.send}.
 *
 * <p>Gemini hooks {@code send} twice, at mixin priority 1000 and 3000, so that one of them always runs before any
 * other mod's hook in between (Inferno uses 2000), whichever way the mixin order falls. The first hook to see a
 * packet runs Gemini's modules on it; with {@link InfernoBypass#tunnel()} on, it then hands the packet straight to
 * the network connection and cancels {@code send}, so hooks further down never see it.
 */
public final class PacketTunnel {
   /** The packet the modules already ran on, so the second hook does not run them again. */
   private static Packet<?> handled;

   private PacketTunnel() {
   }

   /** Returns true when {@code send} should be cancelled. */
   public static boolean onSend(Object listener, Packet<?> packet) {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null) {
         return false;
      }

      if (handled != packet) {
         handled = packet;
         Gemini gemini = Gemini.getInstance();
         if (gemini != null && gemini.modules() != null && gemini.modules().sendPacket(packet)) {
            return true;
         }
      }

      if (InfernoBypass.tunnel() && listener instanceof ClientPacketListener game && game.getConnection() != null) {
         game.getConnection().send(packet);
         return true;
      }

      return false;
   }
}
