package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;

public final class CameraTweaks extends Module {
   public static CameraTweaks instance;
   private final BooleanSetting clip = this.addSetting(new BooleanSetting("Clip Through Blocks", "Third-person camera ignores walls", true));
   private final BooleanSetting custom = this.addSetting(new BooleanSetting("Custom Distance", "Override how far the third-person camera sits", true));
   private final NumberSetting distance = this.addSetting(
      new NumberSetting("Distance", "Third-person camera distance in blocks", 8.0, 1.0, 40.0, 0.5).showIf(this.custom::get)
   );

   public CameraTweaks() {
      super("Camera Tweaks", "Move the third-person camera further out and let it pass through walls.", Category.RENDER);
      instance = this;
   }

   public float distanceOr(float vanilla) {
      return this.custom.get() ? this.distance.floatValue() : vanilla;
   }

   public boolean clips() {
      return this.clip.get();
   }
}
