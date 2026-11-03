package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class ArmorHud extends HudWidget {
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private final ModeSetting layout = this.addSetting(
      new ModeSetting("Layout", "Stack the pieces in a row or a column", "Horizontal", "Horizontal", "Vertical")
   );
   private final BooleanSetting percent = this.addSetting(new BooleanSetting("Percent", "Show remaining durability as a percentage", true));
   private final BooleanSetting hands = this.addSetting(new BooleanSetting("Hands", "Include the items in both hands", false));

   public ArmorHud() {
      super("Armor", "Your armor pieces with their durability.", "Bottom Center", 0, 58);
   }

   private List<ItemStack> stacks(LocalPlayer player) {
      List<ItemStack> list = new ArrayList<>(6);

      for (EquipmentSlot slot : SLOTS) {
         list.add(player.getItemBySlot(slot));
      }

      if (this.hands.get()) {
         list.add(player.getMainHandItem());
         list.add(player.getOffhandItem());
      }

      return list;
   }

   private boolean vertical() {
      return this.layout.is("Vertical");
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      int n = this.stacks(hud.client.player).size();
      return this.vertical() ? 24 + (this.percent.get() ? 4 + hud.font.width("100%") : 0) : 8 + n * 20 - 4;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      int n = this.stacks(hud.client.player).size();
      return this.vertical() ? 8 + n * 18 - 2 : 24 + (this.percent.get() ? 10 : 0);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      List<ItemStack> stacks = this.stacks(hud.client.player);

      for (int i = 0; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         int x = this.vertical() ? 6 : 6 + i * 20;
         int y = this.vertical() ? 4 + i * 18 : 4;
         if (stack.isEmpty()) {
            hud.graphics.fill(x, y, x + 16, y + 16, GeminiMark.withAlpha(-12958120, 60));
         } else {
            hud.graphics.item(stack, x, y);
            hud.graphics.itemDecorations(hud.font, stack, x, y);
            if (this.percent.get() && stack.isDamageableItem()) {
               float ratio = 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage();
               String text = Math.round(ratio * 100.0F) + "%";
               if (this.vertical()) {
                  hud.graphics.text(hud.font, text, x + 20, y + 4, HudHelpers.ratioColor(ratio), hud.shadow);
               } else {
                  hud.graphics.text(hud.font, text, x + 8 - hud.font.width(text) / 2, y + 18, HudHelpers.ratioColor(ratio), hud.shadow);
               }
            }
         }
      }
   }
}
