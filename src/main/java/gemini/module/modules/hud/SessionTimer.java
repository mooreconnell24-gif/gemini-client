package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SessionTimer extends Ticked {
   private final BooleanSetting distance = this.addSetting(new BooleanSetting("Distance", "Also show blocks travelled this session", true));
   private long started;
   private double travelled;

   public SessionTimer() {
      super("Session Timer", "How long you have been playing and how far you walked.", "Bottom Right", 6, 96, 250);
   }

   @Override
   public void onEnable() {
      this.started = System.currentTimeMillis();
      this.travelled = 0.0;
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         double step = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
         if (step < 8.0) {
            this.travelled += step;
         }
      }
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      long seconds = (System.currentTimeMillis() - this.started) / 1000L;
      String time = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
      return !this.distance.get()
         ? List.of(new TextHudWidget.Line("Session " + time, -1511689))
         : List.of(new TextHudWidget.Line("Session " + time, -1511689), new TextHudWidget.Line(Math.round(this.travelled) + " blocks walked", -6641995));
   }
}
