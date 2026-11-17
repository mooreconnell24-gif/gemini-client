package gemini.ui.clickgui.controls;

import gemini.module.setting.ActionSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Button for an {@link ActionSetting}: a left click runs the action. */
public final class ActionControl extends SettingControl {
   private final ActionSetting setting;
   private final Glass.Hover hover = new Glass.Hover();

   public ActionControl(ActionSetting setting) {
      this.setting = setting;
   }

   @Override
   public Setting<?> setting() {
      return this.setting;
   }

   @Override
   public int height() {
      return this.rowHeight() + 4;
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      int h = this.height();
      int left = x + Widgets.PAD;
      int right = x + w - Widgets.PAD;
      boolean over = mouseX != NO_MOUSE && inside(mouseX, mouseY, left, y + 2, right, y + h - 2);
      this.hover.update(over, false, dt);
      Glass.roundRect(g, left, y + 2, right, y + h - 2, 4, Glass.fade(this.hover.fill(Widgets.raised(), Widgets.raisedHover()), this.opacity));
      String label = UiFont.ellipsize(this.setting.name(), right - left - 8, FontSize.BODY, FontWeight.MEDIUM);
      Controls.text(
         g, label, (left + right - UiFont.width(label, FontSize.BODY, FontWeight.MEDIUM)) / 2, UiFont.centerY(y + 2, h - 4), this.hover.label(true), this.opacity,
         FontSize.BODY, FontWeight.MEDIUM
      );
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (button != 0 || !this.contains(mouseX, mouseY)) {
         return false;
      }

      this.hover.flash();
      this.setting.run();
      this.committed();
      return true;
   }
}
