package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.UiFont;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TotemCounter extends HudWidget {
   /** Built on first draw: an ItemStack made while the class loads (before items are bound) crashes the game. */
   private ItemStack totem;
   private int amount = -1;
   private String text = "";

   public TotemCounter() {
      super("Totem Counter", "Shows how many totems of undying you are carrying.", "Bottom Center", 0, 110);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 2 * HudStyle.PAD_X + 16 + 6 + UiFont.width("00");
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 16 + 2 * HudStyle.PAD_Y;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      int count = HudHelpers.count(hud.client.player, Items.TOTEM_OF_UNDYING);
      if (count != this.amount) {
         this.amount = count;
         this.text = Integer.toString(count);
      }

      if (this.totem == null) {
         this.totem = new ItemStack(Items.TOTEM_OF_UNDYING);
      }

      hud.graphics.item(this.totem, HudStyle.PAD_X, HudStyle.PAD_Y);
      // White while you have spares, amber on your last one, red with none.
      int color = count == 0 ? Glass.ERROR : (count == 1 ? Glass.WARN : Glass.TEXT);
      UiFont.draw(hud.graphics, this.text, w - HudStyle.PAD_X - UiFont.width(this.text), HudStyle.PAD_Y + 4, color, hud.shadow);
   }
}
