package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;

public final class FastPlaceModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between placements (vanilla is 4)", 1.0, 0.0, 4.0, 1.0));
   private final BooleanSetting blocksOnly = this.addSetting(new BooleanSetting("Blocks Only", "Only speed up block placement", false));

   public FastPlaceModule() {
      super("FastPlace", "Remove the delay between placing blocks.", Category.PLAYER);
   }

   public int limit(Minecraft client) {
      return this.blocksOnly.get() && client.player != null && !(client.player.getMainHandItem().getItem() instanceof BlockItem)
         ? Integer.MAX_VALUE
         : this.delay.intValue();
   }
}
