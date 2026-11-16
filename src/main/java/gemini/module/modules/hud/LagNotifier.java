package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.protocol.Packet;

public final class LagNotifier extends Ticked {
   private final NumberSetting threshold = this.addSetting(
      new NumberSetting("Threshold", "Seconds without data before the warning shows", 1.0, 0.5, 10.0, 0.5)
   );
   private volatile long lastPacket = System.currentTimeMillis();

   public LagNotifier() {
      super("Lag Notifier", "Warns you when the world or server stops responding.", "Top Center", 0, 56, 100);
   }

   @Override
   public void onEnable() {
      this.lastPacket = System.currentTimeMillis();
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      this.lastPacket = System.currentTimeMillis();
      return false;
   }

   /**
    * In single player the world runs on the integrated server, which sends little when nothing happens, so the gap
    * between packets means nothing there. Instead its tick counter is watched: a world that has not ticked for the
    * threshold, while the game is not paused and the tick rate is not frozen, is stuck (saving, generating, a
    * huge redstone or entity load).
    */
   private int lastServerTick = Integer.MIN_VALUE;
   private long lastServerTickAt;

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      long now = System.currentTimeMillis();
      net.minecraft.server.MinecraftServer server = hud.client.getSingleplayerServer();
      if (server != null) {
         int tick = server.getTickCount();
         if (tick != this.lastServerTick || hud.client.isPaused() || server.tickRateManager().isFrozen()) {
            this.lastServerTick = tick;
            this.lastServerTickAt = now;
            return List.of();
         }

         double stuck = (now - this.lastServerTickAt) / 1000.0;
         return stuck < this.threshold.get()
            ? List.of()
            : List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "World not responding for %.1fs", stuck), gemini.ui.Glass.ERROR));
      }

      double gap = (now - this.lastPacket) / 1000.0;
      return gap < this.threshold.get()
         ? List.of()
         : List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "Server not responding for %.1fs", gap), gemini.ui.Glass.ERROR));
   }
}
