package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class Facing extends Ticked {
   public Facing() {
      super("Facing", "The direction you are looking, with yaw and pitch.", "Top Left", 6, 62, 50);
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
         new TextHudWidget.Line(HudHelpers.pretty(direction.getName()) + " (" + axis + ")", hud.accent),
         new TextHudWidget.Line(String.format(Locale.ROOT, "Yaw %.1f  Pitch %.1f", Mth.wrapDegrees(player.getYRot()), player.getXRot()), -6641995)
      );
   }
}
