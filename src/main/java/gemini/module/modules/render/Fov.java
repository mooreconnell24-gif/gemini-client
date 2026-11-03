package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;

public final class Fov extends Module {
   public static Fov instance;
   private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view in degrees", 100.0, 30.0, 160.0, 1.0));
   private final BooleanSetting keepEffects = this.addSetting(new BooleanSetting("Keep Effects", "Still widen the view while sprinting or hasted", false));

   public Fov() {
      super("FOV", "Set a field of view beyond the vanilla slider.", Category.RENDER);
      instance = this;
   }

   public float apply(float vanilla) {
      if (this.keepEffects.get()) {
         float base = ((Integer)Minecraft.getInstance().options.fov().get()).floatValue();
         return base <= 0.0F ? vanilla : vanilla * this.fov.floatValue() / base;
      } else {
         return this.fov.floatValue();
      }
   }
}
