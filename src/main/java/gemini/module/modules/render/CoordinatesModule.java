package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;

public final class CoordinatesModule extends Module {
   private final ModeSetting position = this.addSetting(
      new ModeSetting("Position", "Screen corner", "Bottom Left", "Bottom Left", "Top Left", "Bottom Right", "Top Right")
   );
   private final BooleanSetting showBiome = this.addSetting(new BooleanSetting("Biome", "Show the biome name", true));
   private final BooleanSetting showDirection = this.addSetting(new BooleanSetting("Facing", "Show the direction you are looking", true));
   private final BooleanSetting nether = this.addSetting(new BooleanSetting("Nether Coords", "Also show the matching Nether or Overworld position", true));

   public CoordinatesModule() {
      super("Coordinates", "XYZ, facing and biome on screen.", Category.RENDER);
   }

   public String position() {
      return this.position.get();
   }

   public boolean showBiome() {
      return this.showBiome.get();
   }

   public boolean showDirection() {
      return this.showDirection.get();
   }

   public boolean showNether() {
      return this.nether.get();
   }
}
