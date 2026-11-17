package gemini.ui.clickgui.controls;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Switch for a {@link BooleanSetting}: the name on the left, a sliding switch on the right. A left click flips it. */
public final class ToggleControl extends SettingControl {
   private final BooleanSetting setting;
   private final Glass.Hover hover = new Glass.Hover();
   private final Glass.Spring knob = new Glass.Spring(Float.NaN, 260.0F, 0.82F);

   public ToggleControl(BooleanSetting setting) {
      this.setting = setting;
   }

   @Override
   public Setting<?> setting() {
      return this.setting;
   }

   @Override
   public int height() {
      return this.rowHeight();
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      int h = this.height();
      boolean over = mouseX != NO_MOUSE && inside(mouseX, mouseY, x, y, x + w, y + h);
      this.hover.update(over, false, dt);
      Widgets.rowHover(g, x, y, w, h, this.hover.hover, this.opacity);
      float position = this.knob.update(this.setting.get() ? 1.0F : 0.0F, dt);
      int left = x + Widgets.PAD;
      int right = x + w - Widgets.PAD;
      Controls.text(
         g, null, UiFont.ellipsize(this.setting.name(), right - left - Widgets.TOGGLE_W - 6), left, UiFont.centerY(y, h),
         Widgets.label(this.setting.isDefault()), this.opacity
      );
      Widgets.toggle(g, right - Widgets.TOGGLE_W, y + (h - Widgets.TOGGLE_H) / 2, position, this.accent(), true, this.opacity);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (button != 0 || !this.contains(mouseX, mouseY)) {
         return false;
      }

      this.setting.toggle();
      this.hover.flash();
      this.committed();
      return true;
   }
}
