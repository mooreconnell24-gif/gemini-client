package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

public final class LightLevel extends Ticked {
   public LightLevel() {
      super("Light Level", "Shows the light level at your feet and whether monsters can spawn there.", "Top Left", 6, 166, 200);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      ClientLevel level = hud.client.level;
      BlockPos pos = hud.client.player.blockPosition();
      int block = level.getBrightness(LightLayer.BLOCK, pos);
      int sky = level.getBrightness(LightLayer.SKY, pos);
      int blockLimit = level.dimensionType().monsterSpawnBlockLightLimit();
      int skyLimit = level.dimensionType().monsterSpawnLightTest().maxInclusive();
      int effective = Math.min(level.getMaxLocalRawBrightness(pos), 15);
      boolean spawnable = block <= blockLimit && effective <= skyLimit;
      return List.of(
         new TextHudWidget.Line("Light " + effective + "  (sky " + sky + " / block " + block + ")", -1511689),
         new TextHudWidget.Line(spawnable ? "Dark enough for monsters" : "Too bright for monsters", spawnable ? -1548963 : -11086174)
      );
   }
}
