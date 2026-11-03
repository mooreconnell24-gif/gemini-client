package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Cooldowns extends HudWidget {
   private static final Item[] TRACKED = new Item[]{Items.ENDER_PEARL, Items.WIND_CHARGE, Items.GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD};

   public Cooldowns() {
      super("Cooldowns", "Live progress bars for Ender Pearl, Wind Charge, Golden Apple, Chorus Fruit, and Shield cooldowns.", "Top Center", -95, 42);
   }

   private List<ItemStack> activeStacks(LocalPlayer player) {
      List<ItemStack> out = new ArrayList<>();
      if (player == null) {
         return out;
      } else {
         for (Item item : TRACKED) {
            ItemStack stack = new ItemStack(item);
            if (player.getCooldowns().isOnCooldown(stack)) {
               out.add(stack);
            }
         }

         return out;
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return 112;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      List<ItemStack> active = this.activeStacks(hud.client.player);
      return active.isEmpty() ? 0 : 8 + active.size() * 18;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer player = hud.client.player;
      List<ItemStack> active = this.activeStacks(player);

      for (int i = 0; i < active.size(); i++) {
         ItemStack stack = active.get(i);
         float pct = player.getCooldowns().getCooldownPercent(stack, hud.partialTick);
         int y = 4 + i * 18;
         hud.graphics.item(stack, 6, y);
         String name = stack.getHoverName().getString();
         hud.graphics.text(hud.font, name, 25, y + 2, -1511689, false);
         int barW = w - 31;
         hud.graphics.fill(25, y + 12, 25 + barW, y + 15, -14142908);
         hud.graphics.fill(25, y + 12, 25 + Math.round(barW * pct), y + 15, hud.accent);
      }
   }
}
