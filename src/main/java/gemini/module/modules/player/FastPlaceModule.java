package gemini.module.modules.player;

import java.util.List;
import java.util.ArrayList;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;

public final class FastPlaceModule extends Module {
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between placements or uses while you hold the use button (vanilla is 4)", 1.0, 0.0, 4.0, 1.0));
   private final BooleanSetting blocksOnly = this.addSetting(new BooleanSetting("Blocks Only", "Only shorten the delay for blocks; off also speeds up experience bottles, snowballs, eggs and potions", false));

   public FastPlaceModule() {
      super("Fast Place", "Shortens the delay between placing blocks, and between throwing or using items, while you hold the use button.", Category.PLAYER);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Fast Use");
      names.add("FastUse");
      return names;
   }

   public int limit(Minecraft client) {
      return this.blocksOnly.get() && client.player != null && !(client.player.getMainHandItem().getItem() instanceof BlockItem)
         ? Integer.MAX_VALUE
         : this.delay.intValue();
   }
}
