package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.protocol.Packet;

public final class LagNotifier extends Ticked {
   private final NumberSetting threshold = this.addSetting(
      new NumberSetting("Threshold", "Seconds without a packet before the warning shows", 1.0, 0.5, 10.0, 0.5)
   );
   private volatile long lastPacket = System.currentTimeMillis();

   public LagNotifier() {
      super("Lag Notifier", "Warns when the server stops sending data.", "Top Center", 0, 56, 100);
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

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      double gap = (System.currentTimeMillis() - this.lastPacket) / 1000.0;
      return !(gap < this.threshold.get()) && !hud.client.hasSingleplayerServer()
         ? List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "Server not responding for %.1fs", gap), -1548963))
         : List.of();
   }
}
