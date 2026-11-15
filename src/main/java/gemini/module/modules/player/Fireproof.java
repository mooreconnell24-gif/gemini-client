package gemini.module.modules.player;

import gemini.module.AttributeModuleBase;
import gemini.module.Category;
import gemini.module.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Shortens how long you keep burning after touching fire or lava. Standing in fire still hurts. */
public final class Fireproof extends AttributeModuleBase {
   private final NumberSetting burn = this.addSetting(new NumberSetting("Burn Time", "Percent of the normal time you stay on fire (0 goes out at once)", 0.0, 0.0, 100.0, 5.0));

   public Fireproof() {
      super("Fireproof", "Stop burning sooner after leaving fire or lava.", Category.PLAYER);
      this.modifier(Attributes.BURNING_TIME, "fireproof", Operation.ADD_MULTIPLIED_TOTAL, () -> this.burn.get() / 100.0 - 1.0);
   }
}
