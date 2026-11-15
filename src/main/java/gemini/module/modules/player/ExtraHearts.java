package gemini.module.modules.player;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Raises your maximum health. The extra hearts start empty and fill as you heal. */
public final class ExtraHearts extends AttributeModuleBase {
   private final NumberSetting hearts = this.addSetting(new NumberSetting("Hearts", "Extra hearts on top of the normal ten", 10.0, 1.0, 40.0, 1.0));

   public ExtraHearts() {
      super("Extra Hearts", "More maximum health while this is on.", Category.PLAYER);
      this.modifier(Attributes.MAX_HEALTH, "extra_hearts", Operation.ADD_VALUE, () -> this.hearts.get() * 2.0);
   }

   @Override
   public String info() {
      return "+" + this.hearts.intValue();
   }
}
