package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.util.Mth;

public final class ChunkInfo extends Ticked {
   public ChunkInfo() {
      super("Chunk Info", "Chunk and region coordinates.", "Top Left", 6, 220, 250);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      int x = Mth.floor(hud.client.player.getX());
      int z = Mth.floor(hud.client.player.getZ());
      int cx = x >> 4;
      int cz = z >> 4;
      return List.of(
         new TextHudWidget.Line("Chunk " + cx + ", " + cz + "  (" + (x & 15) + ", " + (z & 15) + ")", -1511689),
         new TextHudWidget.Line("Region r." + (cx >> 5) + "." + (cz >> 5), -6641995)
      );
   }
}
