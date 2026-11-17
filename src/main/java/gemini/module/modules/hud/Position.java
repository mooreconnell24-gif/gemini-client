package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.HudContext;
import gemini.util.InfoText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class Position extends Ticked {
   private final BooleanSetting decimals = this.addSetting(new BooleanSetting("Decimals", "Show coordinates with decimals", false));
   private final BooleanSetting nether = this.addSetting(new BooleanSetting("Nether Coords", "Also show the matching Nether or Overworld position", true));
   private final BooleanSetting dimension = this.addSetting(new BooleanSetting("Dimension", "Show the dimension name", false));
   private final StringBuilder text = new StringBuilder(48);

   public Position() {
      super("Position", "Shows your coordinates, with the matching Nether or Overworld position.", "Top Left", 6, 34, 100);
      // One of the two widgets on for a fresh install (with the ArrayList): coordinates are what people look for first.
      this.loadState(true, this.getKeybind());
   }

   /** Several numbers in a row read best left-aligned after their label. */
   @Override
   protected boolean alignValuesRight() {
      return false;
   }

   private String join(String a, String b, String c) {
      StringBuilder out = this.text;
      out.setLength(0);
      out.append(a).append("  ").append(b);
      if (c != null) {
         out.append("  ").append(c);
      }

      return out.toString();
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      LocalPlayer player = hud.client.player;
      ClientLevel level = hud.client.level;
      List<TextHudWidget.Line> lines = new ArrayList<>(3);
      if (this.decimals.get()) {
         lines.add(TextHudWidget.Line.row("XYZ", this.join(InfoText.format(player.getX(), 2), InfoText.format(player.getY(), 2), InfoText.format(player.getZ(), 2)), ""));
      } else {
         lines.add(
            TextHudWidget.Line.row(
               "XYZ", this.join(Integer.toString(Mth.floor(player.getX())), Integer.toString(Mth.floor(player.getY())), Integer.toString(Mth.floor(player.getZ()))), ""
            )
         );
      }

      if (this.nether.get() && level != null) {
         boolean inNether = level.dimension() == Level.NETHER;
         if (inNether || level.dimension() == Level.OVERWORLD) {
            double factor = inNether ? 8.0 : 0.125;
            String x = Long.toString(Math.round(player.getX() * factor));
            String z = Long.toString(Math.round(player.getZ() * factor));
            lines.add(TextHudWidget.Line.row(inNether ? "Overworld" : "Nether", this.join(x, z, null), ""));
         }
      }

      if (this.dimension.get() && level != null) {
         lines.add(TextHudWidget.Line.row("World", HudHelpers.pretty(level.dimension().identifier().getPath()), ""));
      }

      return lines;
   }
}
