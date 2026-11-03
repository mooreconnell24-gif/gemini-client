package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TotemCounter extends HudWidget {
   public TotemCounter() {
      super("Totem Counter", "How many Totems of Undying you are carrying.", "Bottom Center", 0, 110);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 30 + hud.font.width("00x");
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 24;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      int amount = HudHelpers.count(hud.client.player, Items.TOTEM_OF_UNDYING);
      hud.graphics.item(new ItemStack(Items.TOTEM_OF_UNDYING), 6, 4);
      hud.graphics.text(hud.font, amount + "x", 28, 8, amount == 0 ? -1548963 : (amount == 1 ? -14249 : -11086174), hud.shadow);
   }
}
