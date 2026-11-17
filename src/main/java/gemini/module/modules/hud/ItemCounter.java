package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * How many of the chosen items you carry: icon and a right-aligned count per row, red when you have none. Counted
 * once a tick into reused lists; a frame only draws.
 */
public final class ItemCounter extends HudWidget {
   private static final int ROW = 18;
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
   private final BooleanSetting hideZero = this.addSetting(new BooleanSetting("Hide Zero", "Leave out items you have none of", false));
   private final List<ItemStack> icons = new ArrayList<>();
   private final List<String> counts = new ArrayList<>();
   private final List<Boolean> empty = new ArrayList<>();
   private int countW;
   private int builtTick = Integer.MIN_VALUE;

   public ItemCounter() {
      super("Item Counter", "Shows how many of the chosen items you carry, with icons.", "Top Center", 0, 90);
   }

   private void rebuild(HudContext hud) {
      LocalPlayer player = hud.client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (tick == this.builtTick) {
         return;
      }

      this.builtTick = tick;
      this.icons.clear();
      this.counts.clear();
      this.empty.clear();
      this.countW = 0;
      if (player == null) {
         return;
      }

      for (Item item : this.watched.items()) {
         int amount = HudHelpers.count(player, item);
         if (this.hideZero.get() && amount <= 0) {
            continue;
         }

         String text = HudFormat.grouped(amount);
         this.icons.add(new ItemStack(item));
         this.counts.add(text);
         this.empty.add(amount <= 0);
         this.countW = Math.max(this.countW, UiFont.width(text));
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.rebuild(hud);
      return this.icons.isEmpty() ? 0 : 2 * HudStyle.PAD_X + 16 + 8 + Math.max(this.countW, UiFont.width("00"));
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.icons.isEmpty() ? 0 : 2 * HudStyle.PAD_Y + this.icons.size() * ROW - 2;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      for (int i = 0; i < this.icons.size(); i++) {
         int y = HudStyle.PAD_Y + i * ROW;
         hud.graphics.item(this.icons.get(i), HudStyle.PAD_X, y);
         String text = this.counts.get(i);
         int color = this.empty.get(i) ? hud.theme.error : hud.text;
         UiFont.draw(hud.graphics, text, w - HudStyle.PAD_X - UiFont.width(text), UiFont.centerY(y, 16), color, hud.shadow);
      }
   }
}
