package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;

public final class Speedometer extends Ticked {
   private final ModeSetting unit = this.addSetting(new ModeSetting("Unit", "How speed is displayed", "Blocks/s", "Blocks/s", "km/h", "Blocks/tick"));
   private final BooleanSetting vertical = this.addSetting(new BooleanSetting("Vertical", "Include vertical speed", false));

   public Speedometer() {
      super("Speedometer", "How fast you are moving.", "Bottom Right", 6, 60, 100);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      double dx = player.getX() - player.xo;
      double dz = player.getZ() - player.zo;
      double dy = this.vertical.get() ? player.getY() - player.yo : 0.0;
      double perTick = Math.sqrt(dx * dx + dy * dy + dz * dz);
      String var12 = this.unit.get();

      String text = switch (var12) {
         case "km/h" -> String.format(Locale.ROOT, "%.1f km/h", perTick * 20.0 * 3.6);
         case "Blocks/tick" -> String.format(Locale.ROOT, "%.3f b/t", perTick);
         default -> String.format(Locale.ROOT, "%.1f b/s", perTick * 20.0);
      };
      return List.of(new TextHudWidget.Line(text, -1511689));
   }
}
