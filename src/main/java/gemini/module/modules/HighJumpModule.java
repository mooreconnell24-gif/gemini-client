package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class HighJumpModule extends Module {
   private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Extra jump strength", 0.4, 0.05, 1.5, 0.05));

   public HighJumpModule() {
      super("HighJump", "Jump much higher than normal.", Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      AttributeBoost.clear(Attributes.JUMP_STRENGTH, "highjump");
   }

   @Override
   public void onTick() {
      AttributeBoost.apply(Attributes.JUMP_STRENGTH, "highjump", this.boost.get(), Operation.ADD_VALUE);
   }
}
