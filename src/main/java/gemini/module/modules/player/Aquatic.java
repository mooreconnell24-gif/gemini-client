package gemini.module.modules.player;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The three things that make water slow, each on its own switch: air, swimming speed and mining speed. */
public final class Aquatic extends AttributeModuleBase {
   private final NumberSetting oxygen = this.addSetting(
      new NumberSetting("Oxygen", "How much longer your air lasts (each point is like one level of Respiration; 0 leaves it alone)", 10.0, 0.0, 100.0, 1.0)
   );
   private final BooleanSetting swim = this.addSetting(new BooleanSetting("Swim Speed", "Move through water as fast as on land", true));
   private final BooleanSetting mining = this.addSetting(new BooleanSetting("Mining Speed", "Mine under water at normal speed", true));

   public Aquatic() {
      super("Aquatic", "Longer breath, full-speed swimming and normal mining speed under water.", Category.PLAYER);
      this.modifier(Attributes.OXYGEN_BONUS, "aquatic_oxygen", Operation.ADD_VALUE, this.oxygen::get);
      this.modifier(Attributes.WATER_MOVEMENT_EFFICIENCY, "aquatic_swim", Operation.ADD_VALUE, () -> this.swim.get() ? 1.0 : 0.0);
      // Vanilla mines at a fifth of the normal speed under water; times five brings it back to normal.
      this.modifier(Attributes.SUBMERGED_MINING_SPEED, "aquatic_mining", Operation.ADD_MULTIPLIED_TOTAL, () -> this.mining.get() ? 4.0 : 0.0);
   }
}
