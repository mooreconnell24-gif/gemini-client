package gemini.module.modules.hud;

import gemini.module.TextHudWidget;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public final class BiomeInfo extends Ticked {
   public BiomeInfo() {
      super("Biome", "The biome you are standing in.", "Top Left", 6, 94, 500);
   }

   @Override
   protected List<TextHudWidget.Line> build(HudContext hud) {
      Holder<Biome> biome = hud.client.level.getBiome(hud.client.player.blockPosition());
      String name = biome.unwrapKey().map(key -> HudHelpers.pretty(key.identifier().getPath())).orElse("Unknown");
      return List.of(new TextHudWidget.Line(name, hud.accent));
   }
}
