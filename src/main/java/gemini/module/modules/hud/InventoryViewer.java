package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Your inventory as a slot grid in the vanilla proportions: 18-pixel slots holding 16-pixel items, the three main
 * rows, and (optionally) the hotbar below them after the same 4-pixel gap the inventory screen uses. Slots are quiet
 * inset squares on the backdrop.
 */
public final class InventoryViewer extends HudWidget {
   private static final int SLOT = 18;
   private static final int HOTBAR_GAP = 4;
   private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Hotbar", "Also show the hotbar row", false));

   public InventoryViewer() {
      super("Inventory Viewer", "Shows the contents of your inventory on screen.", "Bottom Left", 6, 6);
   }

   private int rows() {
      return this.hotbar.get() ? 4 : 3;
   }

   /** The grid sits a little closer to the card edge than text does: the slots carry their own inner space. */
   private static int inset() {
      return HudStyle.PAD_Y;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 2 * inset() + 9 * SLOT;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 2 * inset() + 3 * SLOT + (this.hotbar.get() ? HOTBAR_GAP + SLOT : 0);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      List<ItemStack> items = hud.client.player.getInventory().getNonEquipmentItems();
      int backdrop = this.backdrop();
      // Without a card the slots carry the grid on their own, so they are darker.
      int slotColor = backdrop == HudStyle.NONE ? Glass.withAlpha(hud.surface, 120) : Glass.withAlpha(0xFFFFFF, 16);
      int origin = inset();
      for (int row = 0; row < this.rows(); row++) {
         int y = origin + row * SLOT + (row == 3 ? HOTBAR_GAP : 0);
         for (int column = 0; column < 9; column++) {
            int index = row < 3 ? 9 + row * 9 + column : column;
            int x = origin + column * SLOT;
            Glass.roundRect(g, x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 2, slotColor);
            ItemStack stack = items.get(index);
            if (!stack.isEmpty()) {
               g.item(stack, x + 1, y + 1);
               g.itemDecorations(hud.font, stack, x + 1, y + 1);
            }
         }
      }
   }
}
