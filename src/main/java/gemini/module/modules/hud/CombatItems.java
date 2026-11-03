package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CombatItems extends HudWidget {
   private static final Item[] ITEMS = new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE};

   public CombatItems() {
      super("Combat Items", "Show your live Totem, Crystal, Gapple, and XP counts side by side.", "Bottom Center", -90, 68);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 8 + ITEMS.length * 24;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 28;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      for (int i = 0; i < ITEMS.length; i++) {
         Item item = ITEMS[i];
         int count = HudHelpers.count(hud.client.player, item);
         int x = 6 + i * 24;
         hud.graphics.item(new ItemStack(item), x, 2);
         String label = Integer.toString(count);
         hud.graphics.text(hud.font, label, x + 8 - hud.font.width(label) / 2, 18, count > 0 ? -1511689 : -6641995, hud.shadow);
      }
   }
}
