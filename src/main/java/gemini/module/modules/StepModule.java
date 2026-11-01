package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class StepModule extends Module {
   private final NumberSetting height = this.addSetting(new NumberSetting("Height", "Step height in blocks", 1.0, 0.6, 2.5, 0.1));

   public StepModule() {
      super("Step", "Walk up taller blocks without jumping.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      AttributeBoost.clear(Attributes.STEP_HEIGHT, "step");
   }

   @Override
   public void onTick() {
      AttributeBoost.apply(Attributes.STEP_HEIGHT, "step", Math.max(0.0, this.height.get() - 0.6), Operation.ADD_VALUE);
   }
}
