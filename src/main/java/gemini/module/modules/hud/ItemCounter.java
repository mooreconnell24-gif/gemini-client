package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemCounter extends HudWidget {
   private final ListSetting watched = this.addSetting(
      new ListSetting(
         "Items",
         "Items to count across your inventory and offhand",
         ListSetting.Kind.ITEM,
         "minecraft:totem_of_undying",
         "minecraft:end_crystal",
         "minecraft:experience_bottle",
         "minecraft:golden_apple"
      )
   );
   private final BooleanSetting hideZero = this.addSetting(new BooleanSetting("Hide Zero", "Skip items you have none of", false));

   public ItemCounter() {
      super("Item Counter", "Counts of the items you pick, with icons.", "Top Center", 0, 90);
   }

   private List<Item> rows(LocalPlayer player) {
      List<Item> rows = new ArrayList<>();

      for (Item item : this.watched.items()) {
         if (!this.hideZero.get() || HudHelpers.count(player, item) > 0) {
            rows.add(item);
         }
      }

      return rows;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      List<Item> rows = this.rows(hud.client.player);
      return rows.isEmpty() ? 0 : 30 + hud.font.width("000x");
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      List<Item> rows = this.rows(hud.client.player);
      return rows.isEmpty() ? 0 : 8 + rows.size() * 18 - 2;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      List<Item> rows = this.rows(hud.client.player);

      for (int i = 0; i < rows.size(); i++) {
         Item item = rows.get(i);
         int amount = HudHelpers.count(hud.client.player, item);
         int y = 4 + i * 18;
         hud.graphics.item(new ItemStack(item), 6, y);
         hud.graphics.text(hud.font, amount + "x", 28, y + 4, amount == 0 ? -1548963 : -1511689, hud.shadow);
      }
   }
}
