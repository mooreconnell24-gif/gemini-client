package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

public final class Tps extends Ticked {
   private static final int SAMPLES = 5;
   private final double[] samples = new double[5];
   private int filled;
   private int next;
   private long lastGameTime = Long.MIN_VALUE;
   private long lastReal;
   private volatile double current = 20.0;
   private volatile long lastUpdate;

   public Tps() {
      super("TPS", "Server ticks per second, estimated from time updates.", "Bottom Right", 6, 42, 500);
   }

   @Override
   public void onEnable() {
      this.lastGameTime = Long.MIN_VALUE;
      this.filled = 0;
      this.next = 0;
      this.lastUpdate = 0L;
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (packet instanceof ClientboundSetTimePacket time) {
         long now = System.nanoTime();
         if (this.lastGameTime != Long.MIN_VALUE && time.gameTime() > this.lastGameTime) {
            double seconds = (now - this.lastReal) / 1.0E9;
            if (seconds > 0.2) {
               double rate = Math.min(20.0, (time.gameTime() - this.lastGameTime) / seconds);
               this.samples[this.next] = rate;
               this.next = (this.next + 1) % 5;
               this.filled = Math.min(5, this.filled + 1);
               double sum = 0.0;

               for (int i = 0; i < this.filled; i++) {
                  sum += this.samples[i];
               }

               this.current = sum / this.filled;
               this.lastUpdate = System.currentTimeMillis();
            }
         }

         this.lastGameTime = time.gameTime();
         this.lastReal = now;
      }

      return false;
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      if (hud.client.hasSingleplayerServer()) {
         return List.of(new TextHudWidget.Line("TPS 20.0", -11086174));
      } else if (this.lastUpdate == 0L) {
         return List.of(new TextHudWidget.Line("TPS ...", -6641995));
      } else {
         double tps = this.current;
         return List.of(new TextHudWidget.Line(String.format(Locale.ROOT, "TPS %.1f", tps), tps >= 19.0 ? -11086174 : (tps >= 15.0 ? -14249 : -1548963)));
      }
   }
}
