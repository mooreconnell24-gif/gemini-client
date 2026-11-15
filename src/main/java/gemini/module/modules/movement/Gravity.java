package gemini.module.modules.movement;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Scales how hard gravity pulls on you: moon jumps at 20%, floating at 0%, heavy at 200%. */
public final class Gravity extends AttributeModuleBase {
   private final NumberSetting percent = this.addSetting(new NumberSetting("Gravity", "Percent of normal gravity (100 is vanilla, 0 floats)", 35.0, 0.0, 300.0, 5.0));

   public Gravity() {
      super("Gravity", "Change how strongly gravity pulls on you.", Category.MOVEMENT);
      this.modifier(Attributes.GRAVITY, "gravity", Operation.ADD_MULTIPLIED_TOTAL, () -> this.percent.get() / 100.0 - 1.0);
   }

   @Override
   public String info() {
      return this.percent.intValue() + "%";
   }
}
