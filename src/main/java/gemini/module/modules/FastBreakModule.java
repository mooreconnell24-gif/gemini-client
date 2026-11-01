package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class FastBreakModule extends Module {
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Mining speed multiplier", 2.0, 1.0, 20.0, 0.5));

   public FastBreakModule() {
      super("FastBreak", "Mine blocks faster.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      AttributeBoost.clear(Attributes.BLOCK_BREAK_SPEED, "fastbreak");
   }

   @Override
   public void onTick() {
      AttributeBoost.apply(Attributes.BLOCK_BREAK_SPEED, "fastbreak", this.speed.get() - 1.0, Operation.ADD_MULTIPLIED_TOTAL);
   }
}
