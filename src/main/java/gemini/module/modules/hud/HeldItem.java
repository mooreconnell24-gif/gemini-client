package gemini.module.modules.hud;

import gemini.ui.Glass;
import gemini.module.HudWidget;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The item in your hand: icon, name, and either its durability (remaining / maximum, the remaining part coloured
 * only when it runs low) or the stack size. The texts are rebuilt once a tick.
 */
public final class HeldItem extends HudWidget {
   private int builtTick = Integer.MIN_VALUE;
   private String name;
   private String value = "";
   private String unit = "";
   private int valueColor = Glass.TEXT;

   public HeldItem() {
      super("Held Item", "Shows the item in your hand with its durability.", "Bottom Center", 0, 84);
   }

   private void rebuild(HudContext hud) {
      LocalPlayer player = hud.client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (tick == this.builtTick) {
         return;
      }

      this.builtTick = tick;
      ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
      if (stack.isEmpty()) {
         this.name = null;
         return;
      }

      this.name = stack.getHoverName().getString();
      if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
         int left = stack.getMaxDamage() - stack.getDamageValue();
         this.value = Integer.toString(left);
         this.unit = "/ " + stack.getMaxDamage();
         this.valueColor = HudStyle.level((float)left / stack.getMaxDamage());
      } else {
         this.value = stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "";
         this.unit = "";
         this.valueColor = Glass.TEXT;
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.rebuild(hud);
      if (this.name == null) {
         return 0;
      }

      int tail = this.value.isEmpty() ? 0 : 8 + hud.font.width(this.value) + (this.unit.isEmpty() ? 0 : HudStyle.UNIT_GAP + hud.font.width(this.unit));
      return 2 * HudStyle.PAD_X + 16 + 6 + hud.font.width(this.name) + tail;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.name == null ? 0 : 16 + 2 * HudStyle.PAD_Y;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      if (this.name == null) {
         return;
      }

      ItemStack stack = hud.client.player.getMainHandItem();
      int y = HudStyle.PAD_Y + 4;
      hud.graphics.item(stack, HudStyle.PAD_X, HudStyle.PAD_Y);
      int x = HudStyle.PAD_X + 16 + 6;
      hud.graphics.text(hud.font, this.name, x, y, HudStyle.label(this.backdrop()), hud.shadow);
      if (!this.value.isEmpty()) {
         int right = w - HudStyle.PAD_X;
         int unitW = this.unit.isEmpty() ? 0 : HudStyle.UNIT_GAP + hud.font.width(this.unit);
         hud.graphics.text(hud.font, this.value, right - unitW - hud.font.width(this.value), y, this.valueColor, hud.shadow);
         if (unitW > 0) {
            hud.graphics.text(hud.font, this.unit, right - unitW + HudStyle.UNIT_GAP, y, HudStyle.unit(this.backdrop()), hud.shadow);
         }
      }
   }
}
