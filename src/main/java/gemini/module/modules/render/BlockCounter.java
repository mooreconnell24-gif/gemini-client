package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.ui.HudContext;
import gemini.util.InfoText;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class BlockCounter extends Module {
   private final InfoText text = new InfoText();

   public BlockCounter() {
      super("BlockCounter", "Show how many blocks your hotbar holds in a pill under the crosshair.", Category.RENDER);
   }

   @Override
   public void onRenderHud(HudContext hud) {
      LocalPlayer player = hud.client.player;
      if (player != null) {
         int totalBlocks = 0;
         ItemStack iconStack = ItemStack.EMPTY;

         for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
               totalBlocks += s.getCount();
               if (iconStack.isEmpty()) {
                  iconStack = s;
               }
            }
         }

         if (player.getMainHandItem().getItem() instanceof BlockItem) {
            iconStack = player.getMainHandItem();
         }

         if (totalBlocks > 0 && !iconStack.isEmpty()) {
            String text = this.text.whole(totalBlocks, " blocks");
            int boxW = 24 + hud.font.width(text) + 8;
            int boxH = 20;
            int x = hud.width / 2 - boxW / 2;
            int y = hud.height / 2 + 16;
            hud.graphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 1140897023);
            hud.graphics.fill(x, y, x + boxW, y + boxH, -871559145);
            hud.graphics.fill(x, y + boxH - 2, x + boxW, y + boxH, totalBlocks > 32 ? hud.accent2 : -43691);
            hud.graphics.item(iconStack, x + 4, y + 2);
            hud.graphics.text(hud.font, text, x + 23, y + 6, totalBlocks > 32 ? -1 : -43691, hud.shadow);
         }
      }
   }
}
