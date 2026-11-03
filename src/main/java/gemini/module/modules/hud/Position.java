package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class Position extends Ticked {
   private final BooleanSetting decimals = this.addSetting(new BooleanSetting("Decimals", "Show fractional coordinates", false));
   private final BooleanSetting nether = this.addSetting(new BooleanSetting("Nether Coords", "Also show the matching Nether or Overworld position", true));
   private final BooleanSetting dimension = this.addSetting(new BooleanSetting("Dimension", "Show the dimension name", false));

   public Position() {
      super("Position", "Your coordinates, with the Nether or Overworld equivalent.", "Top Left", 6, 34, 100);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      ClientLevel level = hud.client.level;
      List<TextHudWidget.Line> lines = new ArrayList<>(3);
      if (this.decimals.get()) {
         lines.add(new TextHudWidget.Line(String.format(Locale.ROOT, "XYZ %.2f  %.2f  %.2f", player.getX(), player.getY(), player.getZ()), -1511689));
      } else {
         lines.add(new TextHudWidget.Line("XYZ " + Mth.floor(player.getX()) + "  " + Mth.floor(player.getY()) + "  " + Mth.floor(player.getZ()), -1511689));
      }

      if (this.nether.get() && level != null) {
         boolean inNether = level.dimension() == Level.NETHER;
         if (inNether || level.dimension() == Level.OVERWORLD) {
            double factor = inNether ? 8.0 : 0.125;
            lines.add(
               new TextHudWidget.Line(
                  (inNether ? "Overworld " : "Nether ") + Math.round(player.getX() * factor) + "  " + Math.round(player.getZ() * factor), -6641995
               )
            );
         }
      }

      if (this.dimension.get() && level != null) {
         lines.add(new TextHudWidget.Line(HudHelpers.pretty(level.dimension().identifier().getPath()), hud.accent));
      }

      return lines;
   }
}
