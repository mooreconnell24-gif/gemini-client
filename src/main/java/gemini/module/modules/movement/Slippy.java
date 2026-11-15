package gemini.module.modules.movement;

import gemini.util.InfoText;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;

public final class Slippy extends Module {
   public static Slippy instance;
   private final NumberSetting friction = this.addSetting(
      new NumberSetting("Friction", "Block friction", 0.98, 0.6, 1.0, 0.01)
   );
   private final ListSetting ignored = this.addSetting(new ListSetting("Ignored Blocks", "Blocks that keep their normal friction", ListSetting.Kind.BLOCK));

   public Slippy() {
      super("Slippy", "Make every block as slippery as ice.", Category.MOVEMENT);
      instance = this;
   }

   private final InfoText infoText = new InfoText();

   @Override
   public String info() {
      return this.infoText.fixed(this.friction.get(), 2);
   }

   public float override(net.minecraft.world.level.block.Block block, float original) {
      return this.ignored.blocks().contains(block) ? original : this.friction.floatValue();
   }
}
