package gemini.module.modules.player;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.NumberSetting;
import java.util.Locale;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Makes you smaller or larger; your hitbox, eye height and step change with it, as the game's scale attribute does. */
public final class Size extends AttributeModuleBase {
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Your size as a multiple of normal (1 is vanilla)", 0.5, 0.1, 4.0, 0.1));

   public Size() {
      super("Size", "Shrink to fit through one-block gaps or grow into a giant.", Category.PLAYER);
      this.modifier(Attributes.SCALE, "size", Operation.ADD_MULTIPLIED_TOTAL, () -> this.scale.get() - 1.0);
   }

   @Override
   public String info() {
      return String.format(Locale.ROOT, "x%.1f", this.scale.get());
   }
}
