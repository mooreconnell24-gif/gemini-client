package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;

public final class ZoomModule extends Module {
   private final NumberSetting factor = this.addSetting(new NumberSetting("Factor", "How far the view zooms in", 4.0, 1.5, 12.0, 0.5));
   private final BooleanSetting smooth = this.addSetting(new BooleanSetting("Smooth", "Ease in and out of the zoom", true));
   private float current = 1.0F;

   public ZoomModule() {
      super("Zoom", "Optic zoom. Bind it to a key and hold it.", Category.RENDER, 67);
      this.holdByDefault();
   }

   @Override
   public void onEnable() {
      this.current = this.smooth.get() ? 1.0F : this.factor.floatValue();
   }

   @Override
   public void onDisable() {
      this.current = 1.0F;
   }

   @Override
   public void onTick() {
      float target = this.factor.floatValue();
      this.current = this.smooth.get() ? this.current + (target - this.current) * 0.35F : target;
   }

   public float multiplier() {
      return Math.max(1.0F, this.current);
   }
}
