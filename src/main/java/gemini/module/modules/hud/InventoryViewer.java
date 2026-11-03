package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class InventoryViewer extends HudWidget {
   private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Hotbar", "Also draw the hotbar row", false));

   public InventoryViewer() {
      super("Inventory Viewer", "Your inventory on screen.", "Bottom Left", 6, 6);
   }

   private int rows() {
      return this.hotbar.get() ? 4 : 3;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 168;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 8 + this.rows() * 18 - 2;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      List<ItemStack> items = hud.client.player.getInventory().getNonEquipmentItems();

      for (int row = 0; row < this.rows(); row++) {
         for (int column = 0; column < 9; column++) {
            int index = row < 3 ? 9 + row * 9 + column : column;
            int x = 6 + column * 18;
            int y = 4 + row * 18;
            hud.graphics.fill(x, y, x + 16, y + 16, GeminiMark.withAlpha(-12958120, 70));
            ItemStack stack = items.get(index);
            if (!stack.isEmpty()) {
               hud.graphics.item(stack, x, y);
               hud.graphics.itemDecorations(hud.font, stack, x, y);
            }
         }
      }
   }
}
