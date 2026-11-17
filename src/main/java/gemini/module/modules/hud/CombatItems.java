package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.UiFont;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CombatItems extends HudWidget {
   private static final Item[] ITEMS = new Item[]{Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.ENCHANTED_GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE};
   private static final int CELL = 24;
   private final int[] counts = {-1, -1, -1, -1};
   private final String[] texts = {"", "", "", ""};

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
      return 2 * HudStyle.PAD_X + ITEMS.length * CELL - (CELL - 16);
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 2 * HudStyle.PAD_Y + 16 + 2 + 8;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      int backdrop = this.backdrop();
      for (int i = 0; i < ITEMS.length; i++) {
         int count = HudHelpers.count(hud.client.player, ITEMS[i]);
         if (count != this.counts[i]) {
            this.counts[i] = count;
            this.texts[i] = Integer.toString(count);
         }

         int x = HudStyle.PAD_X + i * CELL;
         hud.graphics.item(this.icons()[i], x, HudStyle.PAD_Y);
         String label = this.texts[i];
         // Counts are centred under their icon; an item you have none of is dimmed rather than coloured.
         int color = count > 0 ? hud.text : HudStyle.unit(backdrop);
         UiFont.draw(hud.graphics, label, x + (16 - UiFont.width(label) + 1) / 2, HudStyle.PAD_Y + 18, color, hud.shadow);
      }
   }
}
