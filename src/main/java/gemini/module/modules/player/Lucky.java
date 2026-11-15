package gemini.module.modules.player;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Raises the luck attribute, which improves fishing loot and any loot table that uses luck. */
public final class Lucky extends AttributeModuleBase {
   private final NumberSetting luck = this.addSetting(new NumberSetting("Luck", "Luck points (the Luck potion gives 1)", 10.0, 1.0, 100.0, 1.0));

   public Lucky() {
      super("Lucky", "Better fishing and chest loot through the luck attribute.", Category.PLAYER);
      this.modifier(Attributes.LUCK, "lucky", Operation.ADD_VALUE, this.luck::get);
   }

   @Override
   public String info() {
      return "+" + this.luck.intValue();
   }
}
