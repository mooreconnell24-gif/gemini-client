package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import java.util.List;
import gemini.util.InfoText;
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
      super("TPS", "Shows the world's ticks per second and how long each tick takes.", "Bottom Right", 6, 42, 500);
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
      net.minecraft.server.MinecraftServer server = hud.client.getSingleplayerServer();
      if (server != null) {
         // Single player: read the integrated server directly instead of assuming 20.
         float target = server.tickRateManager().tickrate();
         if (server.tickRateManager().isFrozen()) {
            return List.of(TextHudWidget.Line.row("", "frozen", "tps", Glass.TEXT_DIM));
         }

         double mspt = server.getAverageTickTimeNanos() / 1.0E6;
         double tps = mspt <= 0.0 ? target : Math.min(target, 1000.0 / mspt);
         int color = tps >= target * 0.95 ? Glass.TEXT : (tps >= target * 0.75 ? Glass.WARN : Glass.ERROR);
         // Value first; the tick time follows as a quiet second figure.
         return List.of(TextHudWidget.Line.row("", InfoText.format(tps, 1), "tps  " + InfoText.format(mspt, 1) + " ms", color));
      } else if (this.lastUpdate == 0L) {
         return List.of();
      } else {
         double tps = this.current;
         return List.of(TextHudWidget.Line.row("", InfoText.format(tps, 1), "tps", tps >= 19.0 ? Glass.TEXT : (tps >= 15.0 ? Glass.WARN : Glass.ERROR)));
      }
   }
}
