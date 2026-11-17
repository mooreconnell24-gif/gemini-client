package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class Facing extends Ticked {
   public Facing() {
      super("Facing", "Shows the direction you are looking, with yaw and pitch.", "Top Left", 6, 62, 50);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      Direction direction = Direction.fromYRot(player.getYRot());

      String axis = switch (direction) {
         case NORTH -> "-Z";
         case SOUTH -> "+Z";
         case EAST -> "+X";
         case WEST -> "-X";
         default -> "";
      };
      return List.of(
         TextHudWidget.Line.row("Facing", HudHelpers.pretty(direction.getName()), axis),
         TextHudWidget.Line.row("Yaw", gemini.util.InfoText.format(Mth.wrapDegrees(player.getYRot()), 1), ""),
         TextHudWidget.Line.row("Pitch", gemini.util.InfoText.format(player.getXRot(), 1), "")
      );
   }
}
