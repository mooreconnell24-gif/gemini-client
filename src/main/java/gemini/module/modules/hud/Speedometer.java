package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.HudContext;
import gemini.util.InfoText;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;

public final class Speedometer extends Ticked {
   private final ModeSetting unit = this.addSetting(new ModeSetting("Unit", "Unit the speed is shown in", "Blocks/s", "Blocks/s", "km/h", "Blocks/tick"));
   private final BooleanSetting vertical = this.addSetting(new BooleanSetting("Vertical", "Include vertical speed", false));

   public Speedometer() {
      super("Speedometer", "Shows how fast you are moving.", "Bottom Right", 6, 60, 100);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      double dx = player.getX() - player.xo;
      double dz = player.getZ() - player.zo;
      double dy = this.vertical.get() ? player.getY() - player.yo : 0.0;
      double perTick = Math.sqrt(dx * dx + dy * dy + dz * dz);
      // Value first with a small unit; a fixed number of decimals per unit so the width does not jitter.
      return switch (this.unit.get()) {
         case "km/h" -> List.of(TextHudWidget.Line.row("", InfoText.format(perTick * 20.0 * 3.6, 1), "km/h"));
         case "Blocks/tick" -> List.of(TextHudWidget.Line.row("", InfoText.format(perTick, 3), "b/t"));
         default -> List.of(TextHudWidget.Line.row("", InfoText.format(perTick * 20.0, 1), "b/s"));
      };
   }
}
