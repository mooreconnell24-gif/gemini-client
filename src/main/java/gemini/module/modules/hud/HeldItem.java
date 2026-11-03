package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.ui.HudContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class HeldItem extends HudWidget {
   public HeldItem() {
      super("Held Item", "The item in your hand with its durability.", "Bottom Center", 0, 84);
   }

   private String label(LocalPlayer player) {
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty()) {
         return null;
      } else {
         String name = stack.getHoverName().getString();
         if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            return name + "  " + left + "/" + stack.getMaxDamage();
         } else {
            return stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
         }
      }
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      String label = this.label(hud.client.player);
      return label == null ? 0 : 28 + hud.font.width(label);
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.label(hud.client.player) == null ? 0 : 24;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer player = hud.client.player;
      ItemStack stack = player.getMainHandItem();
      String label = this.label(player);
      if (label != null) {
         hud.graphics.item(stack, 6, 4);
         float ratio = stack.isDamageableItem() ? 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage() : 1.0F;
         hud.graphics.text(hud.font, label, 26, 8, stack.isDamageableItem() ? HudHelpers.ratioColor(ratio) : -1511689, hud.shadow);
      }
   }
}
