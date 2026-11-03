package gemini.module.modules.misc;

import net.minecraft.network.protocol.Packet;

/** Shared helpers for the misc modules (formerly statics on the module container classes). */
public final class MiscHelpers {
   private MiscHelpers() {
   }

   static String simplePacketName(Packet<?> packet) {
      String name = packet.getClass().getName();
      return name.substring(name.lastIndexOf(46) + 1);
   }
}
