package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Your armour (and optionally both hands) with durability. Each damageable piece gets a thin bar under its icon
 * that drains as it wears; with Percent on, the remaining durability is written too, in a column that lines up.
 * Bars and numbers stay neutral until a piece is nearly broken (amber under 30 %, red under 12 %).
 *
 * <p>The six possible stacks are read into one reused array per frame; nothing is allocated while drawing. The
 * vanilla durability bar is not drawn on top, so each piece shows its wear once.
 */
public final class ArmorHud extends HudWidget {
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final int ICON = 16;
   private final ModeSetting layout = this.addSetting(
      new ModeSetting("Layout", "Show the pieces in a row or a column", "Horizontal", "Horizontal", "Vertical")
   );
   private final BooleanSetting percent = this.addSetting(new BooleanSetting("Percent", "Show remaining durability as a percentage", true));
   private final BooleanSetting hands = this.addSetting(new BooleanSetting("Hands", "Include the items in both hands", false));
   private final ItemStack[] stacks = new ItemStack[6];
   private int count;
   /** Percent texts for 0..100, built once, so a frame never formats a number. */
   private static final String[] PERCENTS = new String[101];

   static {
      for (int i = 0; i <= 100; i++) {
         PERCENTS[i] = i + "%";
      }
   }

   public ArmorHud() {
      super("Armor", "Shows your armour pieces with their durability.", "Bottom Center", 0, 58);
   }

   /** Fills the reused array with this frame's pieces; returns whether any is worn or held. */
   private boolean collect(LocalPlayer player) {
      int n = 0;
      boolean any = false;
      for (EquipmentSlot slot : SLOTS) {
         ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemBySlot(slot);
         this.stacks[n++] = stack;
         any |= !stack.isEmpty();
      }

      if (this.hands.get()) {
         ItemStack main = player == null ? ItemStack.EMPTY : player.getMainHandItem();
         ItemStack off = player == null ? ItemStack.EMPTY : player.getOffhandItem();
         this.stacks[n++] = main;
         this.stacks[n++] = off;
         any |= !main.isEmpty() || !off.isEmpty();
      }

      this.count = n;
      return any;
   }

   private boolean vertical() {
      return this.layout.is("Vertical");
   }

   /** Width of one piece's column in the horizontal layout: the icon, or the widest percentage. */
   private int cell(Font font) {
      return this.percent.get() ? Math.max(ICON + 4, font.width("100%") + 6) : ICON + 4;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      // Nothing worn or held: no row of empty boxes.
      if (!this.collect(hud.client.player)) {
         return 0;
      }

      if (this.vertical()) {
         return 2 * HudStyle.PAD_X + ICON + (this.percent.get() ? 6 + hud.font.width("100%") : 0);
      }

      return 2 * HudStyle.PAD_X + this.count * this.cell(hud.font) - 4;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      if (this.vertical()) {
         return 2 * HudStyle.PAD_Y + this.count * (ICON + 4) - 4 + 3;
      }

      return 2 * HudStyle.PAD_Y + ICON + 3 + (this.percent.get() ? 2 + 8 : 0);
   }

   private static float durability(ItemStack stack) {
      return stack.isDamageableItem() && stack.getMaxDamage() > 0 ? 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage() : -1.0F;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      Font font = hud.font;
      int backdrop = this.backdrop();
      boolean vertical = this.vertical();
      int cell = this.cell(font);
      for (int i = 0; i < this.count; i++) {
         ItemStack stack = this.stacks[i];
         int x = vertical ? HudStyle.PAD_X : HudStyle.PAD_X + i * cell + (cell - 4 - ICON) / 2;
         int y = vertical ? HudStyle.PAD_Y + i * (ICON + 4) : HudStyle.PAD_Y;
         if (stack.isEmpty()) {
            // An empty slot: a faint outline of where the piece would be.
            Glass.roundOutline(g, x + 1, y + 1, x + ICON - 1, y + ICON - 1, 2, Glass.withAlpha(0xFFFFFF, 26));
            continue;
         }

         g.item(stack, x, y);
         float left = durability(stack);
         if (left < 0.0F) {
            // Not damageable (a hand item): the count, if any, the vanilla way.
            if (stack.getCount() > 1) {
               g.itemDecorations(font, stack, x, y);
            }

            continue;
         }

         int color = HudStyle.level(left);
         HudStyle.bar(g, x + 1, y + ICON + 1, x + ICON - 1, y + ICON + 3, left, color == Glass.TEXT ? Glass.withAlpha(Glass.TEXT, 210) : color, backdrop, 1.0F);
         if (this.percent.get()) {
            String text = PERCENTS[Math.round(left * 100.0F)];
            if (vertical) {
               // Right-aligned in a column so the digits line up down the list.
               g.text(font, text, w - HudStyle.PAD_X - font.width(text), y + 5, color == Glass.TEXT ? HudStyle.label(backdrop) : color, hud.shadow);
            } else {
               g.text(font, text, x + (ICON - font.width(text) + 1) / 2, y + ICON + 5, color == Glass.TEXT ? HudStyle.label(backdrop) : color, hud.shadow);
            }
         }
      }
   }
}
