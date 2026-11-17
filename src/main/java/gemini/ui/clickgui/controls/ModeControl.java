package gemini.ui.clickgui.controls;

import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.Theme;
import gemini.ui.clickgui.Icons;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Picker for a {@link ModeSetting}. A few short options that fit beside the name are a segmented control: every
 * option is visible and the highlight slides to the one picked. Anything longer is a dropdown: the row shows the
 * current value, a left click opens the options underneath (the height eases) and a click on one picks it.
 * A right click always steps back one option, as before.
 */
public final class ModeControl extends SettingControl {
   /** Most options a segmented control shows; more always use the dropdown. */
   public static final int MAX_SEGMENTS = 4;
   private static final int SEGMENT_PAD = 6;
   private static final int OPTION_H = 14;
   private final ModeSetting setting;
   private final Glass.Hover hover = new Glass.Hover();
   private final Glass.Spring pillX = new Glass.Spring(Float.NaN, 300.0F, 0.9F);
   private final Glass.Spring pillW = new Glass.Spring(Float.NaN, 300.0F, 0.9F);
   private boolean open;
   private float expand;
   private boolean segmented;
   private int measuredFor = -1;
   private int measuredGeneration = -1;
   private int[] segmentWidths = new int[0];
   private int segmentsTotal;

   public ModeControl(ModeSetting setting) {
      this.setting = setting;
   }

   @Override
   public Setting<?> setting() {
      return this.setting;
   }

   /** True while the dropdown is open. */
   public boolean open() {
      return this.open;
   }

   /**
    * Whether {@code count} options whose labels total {@code optionsWidth} fit as a segmented control beside a name
    * {@code labelWidth} wide, in a row with {@code available} between its paddings. Pure.
    */
   public static boolean fitsSegmented(int count, int optionsWidth, int labelWidth, int available) {
      return count >= 2 && count <= MAX_SEGMENTS && optionsWidth + count * SEGMENT_PAD * 2 + 2 + 8 + labelWidth <= available;
   }

   /** Height of the open dropdown's list for {@code count} options. Pure. */
   public static int listHeight(int count) {
      return count * OPTION_H + 4;
   }

   private void measure(int w) {
      int generation = UiFont.generation();
      if (w == this.measuredFor && generation == this.measuredGeneration) {
         return;
      }

      this.measuredFor = w;
      this.measuredGeneration = generation;
      List<String> modes = this.setting.modes();
      this.segmentWidths = new int[modes.size()];
      int total = 0;
      for (int i = 0; i < modes.size(); i++) {
         this.segmentWidths[i] = UiFont.width(modes.get(i), FontSize.SMALL, FontWeight.MEDIUM);
         total += this.segmentWidths[i];
      }

      this.segmented = fitsSegmented(modes.size(), total, UiFont.width(this.setting.name()), w - Widgets.PAD * 2);
      this.segmentsTotal = total + modes.size() * SEGMENT_PAD * 2 + 2;
      if (this.segmented) {
         this.open = false;
         this.expand = 0.0F;
      }
   }

   @Override
   public int height() {
      return this.rowHeight() + this.listShown();
   }

   private int listShown() {
      return this.segmented ? 0 : Math.round(listHeight(this.setting.modes().size()) * Glass.easeInOut(this.expand));
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      this.measure(w);
      int row = this.rowHeight();
      boolean hasMouse = mouseX != NO_MOUSE;
      boolean overRow = hasMouse && inside(mouseX, mouseY, x, y, x + w, y + row);
      this.hover.update(overRow, false, dt);
      this.expand = Glass.approach(this.expand, this.open ? 1.0F : 0.0F, 14.0F, dt);
      int left = x + Widgets.PAD;
      int right = x + w - Widgets.PAD;
      int textY = UiFont.centerY(y, row);
      int label = Widgets.label(this.setting.isDefault());
      List<String> modes = this.setting.modes();
      int selected = Math.max(0, modes.indexOf(this.setting.get()));
      if (this.segmented) {
         int bedL = right - this.segmentsTotal;
         int bedT = y + (row - 14) / 2;
         Controls.text(g, null, UiFont.ellipsize(this.setting.name(), bedL - left - 6), left, textY, label, this.opacity);
         Glass.roundRect(g, bedL, bedT, right, bedT + 14, 4, Glass.fade(Widgets.well(), this.opacity));
         int sx = bedL + 1;
         int targetX = sx;
         for (int i = 0; i < selected; i++) {
            targetX += this.segmentWidths[i] + SEGMENT_PAD * 2;
         }

         int targetW = this.segmentWidths[selected] + SEGMENT_PAD * 2;
         float px = this.pillX.update(targetX - bedL, dt);
         float pw = this.pillW.update(targetW, dt);
         int pillL = bedL + Math.round(px);
         Glass.roundRect(g, pillL, bedT + 1, pillL + Math.round(pw), bedT + 13, 3, Glass.fade(Widgets.raised(), this.opacity));
         for (int i = 0; i < modes.size(); i++) {
            int sw = this.segmentWidths[i] + SEGMENT_PAD * 2;
            boolean over = hasMouse && inside(mouseX, mouseY, sx, y, sx + sw, y + row);
            int color = i == selected ? Glass.text() : (over ? Glass.lerp(Glass.textDim(), Glass.text(), 0.6F) : Glass.textDim());
            Controls.text(g, modes.get(i), sx + SEGMENT_PAD, UiFont.centerY(bedT, 14, FontSize.SMALL), color, this.opacity, FontSize.SMALL, FontWeight.MEDIUM);
            sx += sw;
         }

         return;
      }

      Widgets.rowHover(g, x, y, w, row, Math.max(this.hover.hover, this.expand * 0.6F), this.opacity);
      String value = UiFont.ellipsize(this.setting.get(), Math.max(24, (right - left) / 2 + 8), FontSize.SMALL, FontWeight.MEDIUM);
      int valueW = UiFont.width(value, FontSize.SMALL, FontWeight.MEDIUM);
      int chipL = right - valueW - 20;
      int chipT = y + (row - 14) / 2;
      Glass.roundRect(g, chipL, chipT, right, chipT + 14, 4, Glass.fade(this.hover.fill(Widgets.raised(), Widgets.raisedHover()), this.opacity));
      Controls.text(g, value, chipL + 6, UiFont.centerY(chipT, 14, FontSize.SMALL), Glass.text(), this.opacity, FontSize.SMALL, FontWeight.MEDIUM);
      Widgets.icon(g, Icons.Icon.CHEVRON_DOWN, right - 12, chipT + 3, 8, this.open ? this.accent() : Glass.textDim(), this.opacity);
      Controls.text(g, null, UiFont.ellipsize(this.setting.name(), chipL - left - 6), left, textY, label, this.opacity);
      int shown = this.listShown();
      if (shown <= 0) {
         return;
      }

      float fade = this.opacity * this.expand;
      int listTop = y + row;
      g.enableScissor(x, listTop, x + w, listTop + shown);
      Glass.roundRect(g, left - 2, listTop, right, listTop + listHeight(modes.size()) - 2, 4, Glass.fade(Widgets.well(), fade));
      int oy = listTop + 1;
      for (int i = 0; i < modes.size(); i++) {
         boolean over = hasMouse && inside(mouseX, mouseY, left - 2, oy, right, oy + OPTION_H) && mouseY < listTop + shown;
         if (over) {
            Glass.roundRect(g, left - 1, oy, right - 1, oy + OPTION_H, 3, Glass.fade(Glass.withAlpha(Glass.text(), 16), fade));
         }

         boolean current = i == selected;
         int color = current ? Glass.text() : (over ? Glass.lerp(Glass.textDim(), Glass.text(), 0.6F) : Glass.textDim());
         Controls.text(g, null, UiFont.ellipsize(modes.get(i), right - left - 18), left + 4, UiFont.centerY(oy, OPTION_H), color, fade);
         if (current) {
            Widgets.icon(g, Icons.Icon.CHECK, right - 12, oy + 3, 8, this.accent(), fade);
         }

         oy += OPTION_H;
      }

      g.disableScissor();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (!this.contains(mouseX, mouseY) || button == 2) {
         return false;
      }

      int row = this.rowHeight();
      List<String> modes = this.setting.modes();
      if (button == 1) {
         this.setting.cycleBack();
         this.committed();
         return true;
      }

      if (mouseY >= this.y + row) {
         int index = (int)((mouseY - this.y - row - 1) / OPTION_H);
         if (this.open && index >= 0 && index < modes.size()) {
            this.setting.set(modes.get(index));
            this.open = false;
            this.committed();
         }

         return true;
      }

      if (this.segmented) {
         int sx = this.x + this.w - Widgets.PAD - this.segmentsTotal + 1;
         if (mouseX < sx) {
            // a click on the name steps forward, like a click on any other mode row
            this.setting.cycle();
            this.committed();
            return true;
         }

         for (int i = 0; i < modes.size(); i++) {
            int sw = this.segmentWidths[i] + SEGMENT_PAD * 2;
            if (mouseX < sx + sw || i == modes.size() - 1) {
               this.setting.set(modes.get(i));
               this.committed();
               break;
            }

            sx += sw;
         }

         return true;
      }

      this.open = !this.open;
      return true;
   }

   /** A click elsewhere closes the dropdown. */
   @Override
   public void blur() {
      this.open = false;
   }

   @Override
   public String hint() {
      return this.segmented ? "right-click steps back" : "click to pick  ·  right-click steps back";
   }

   /** The theme switcher and other hosts restyle from this: the pill colour follows {@link Theme#current()}. */
   public boolean segmented() {
      return this.segmented;
   }
}
