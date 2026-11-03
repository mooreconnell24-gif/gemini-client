package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import net.minecraft.network.protocol.Packet;

public final class PacketLogger extends Module {
   private final BooleanSetting outgoing = this.addSetting(new BooleanSetting("Outgoing", "Log packets sent to the server", true));
   private final BooleanSetting incoming = this.addSetting(new BooleanSetting("Incoming", "Log packets received from the server", false));
   private final BooleanSetting skipNoise = this.addSetting(
      new BooleanSetting("Skip Noise", "Hide movement, keep-alive and other packets sent every tick", true)
   );
   private String lastLine;
   private int repeats;
   private long lastTime;

   public PacketLogger() {
      super("PacketLogger", "Log outgoing and incoming network packets in chat.", Category.CLIENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.lastLine = null;
      this.repeats = 0;
   }

   @Override
   public boolean onSendPacket(Packet<?> packet) {
      if (this.outgoing.get()) {
         this.log("§b[OUT] §7", packet);
      }

      return false;
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (this.incoming.get()) {
         this.log("§a[IN] §7", packet);
      }

      return false;
   }

   private void log(String prefix, Packet<?> packet) {
      String name = MiscHelpers.simplePacketName(packet);
      if (this.skipNoise.get() && noisy(name)) {
         return;
      }

      // Collapse bursts of the same packet into one line with a count instead of flooding chat.
      String line = prefix + name;
      long now = System.currentTimeMillis();
      if (line.equals(this.lastLine) && now - this.lastTime < 1000L) {
         this.repeats++;
         this.lastTime = now;
         return;
      }

      if (this.repeats > 0) {
         CommandManager.feedback(this.lastLine + " §8x" + (this.repeats + 1));
      }

      this.lastLine = line;
      this.lastTime = now;
      this.repeats = 0;
      CommandManager.feedback(line);
   }

   private static boolean noisy(String name) {
      return name.contains("MovePlayer")
         || name.contains("KeepAlive")
         || name.contains("ClientTickEnd")
         || name.contains("PlayerInput")
         || name.contains("Pong")
         || name.contains("Ping")
         || name.contains("SetTime")
         || name.contains("MoveEntity")
         || name.contains("TeleportEntity")
         || name.contains("RotateHead")
         || name.contains("EntityPositionSync")
         || name.contains("SetEntityMotion")
         || name.contains("SetEntityData")
         || name.contains("BundleDelimiter")
         || name.contains("ChunkBatch")
         || name.contains("LevelChunk")
         || name.contains("LightUpdate")
         || name.contains("SoundEntity")
         || name.contains("Sound");
   }
}
