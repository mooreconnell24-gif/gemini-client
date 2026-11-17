package gemini.module.modules.render;

import java.util.List;
import java.util.ArrayList;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;

public final class CameraTweaks extends Module {
   public static CameraTweaks instance;
   private final BooleanSetting clip = this.addSetting(new BooleanSetting("Clip Through Blocks", "Let the third-person camera pass through walls", true));
   private final BooleanSetting custom = this.addSetting(new BooleanSetting("Custom Distance", "Change how far away the third-person camera sits", true));
   private final NumberSetting distance = this.addSetting(
      new NumberSetting("Distance", "Third-person camera distance, in blocks", 8.0, 1.0, 40.0, 0.5).showIf(this.custom::get)
   );

   public CameraTweaks() {
      super("Camera Clip", "Moves the third-person camera farther out and lets it pass through walls.", Category.RENDER);
      instance = this;
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Camera Tweaks");
      names.add("CameraTweaks");
      return names;
   }

   public float distanceOr(float vanilla) {
      return this.custom.get() ? this.distance.floatValue() : vanilla;
   }

   public boolean clips() {
      return this.clip.get();
   }
}
