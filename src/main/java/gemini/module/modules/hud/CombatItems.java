package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CombatItems extends HudWidget {
   private static final Item[] ITEMS = new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE};

   /** Drawn only, so built once on first draw: not while the class loads, which is before items are bound. */
   private ItemStack[] icons;

   private ItemStack[] icons() {
      if (this.icons == null) {
         ItemStack[] built = new ItemStack[ITEMS.length];
         for (int i = 0; i < ITEMS.length; i++) {
            built[i] = new ItemStack(ITEMS[i]);
         }

         this.icons = built;
      }

      return this.icons;
   }

   public CombatItems() {
      super("Combat Items", "Shows how many totems, crystals, golden apples and experience bottles you carry.", "Bottom Center", -90, 68);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      // 6 on each side of the icons
      return 6 + ITEMS.length * 24 - 8 + 6;
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
         hud.graphics.item(this.icons()[i], x, 2);
         String label = Integer.toString(count);
         hud.graphics.text(hud.font, label, x + 8 - hud.font.width(label) / 2, 18, count > 0 ? -1511689 : -6641995, hud.shadow);
      }
   }
}
