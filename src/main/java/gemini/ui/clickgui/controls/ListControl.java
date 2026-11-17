package gemini.ui.clickgui.controls;

import gemini.module.setting.ListSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.clickgui.Icons;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Row for a {@link ListSetting}: the name, how many entries it holds and an arrow. A left click opens the list
 * editor through the host's opener ({@link #setOpener}); the control itself never changes screens.
 */
public final class ListControl extends SettingControl {
   private final ListSetting setting;
   private final Glass.Hover hover = new Glass.Hover();
   private Runnable opener;

   public ListControl(ListSetting setting) {
      this.setting = setting;
   }

   /** What a left click runs: the host opens its list editor for this setting. */
   public void setOpener(Runnable opener) {
      this.opener = opener;
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
      int left = x + Widgets.PAD;
      int right = x + w - Widgets.PAD;
      int size = this.setting.size();
      String value = size == 0 ? "Empty" : (size == 1 ? "1 entry" : size + " entries");
      int valueW = UiFont.width(value);
      int textY = UiFont.centerY(y, h);
      Widgets.icon(g, Icons.Icon.CHEVRON_RIGHT, right - 8, y + (h - 8) / 2, 8, this.hover.label(true), this.opacity);
      Controls.text(g, null, value, right - 11 - valueW, textY, this.hover.label(true), this.opacity);
      Controls.text(g, null, UiFont.ellipsize(this.setting.name(), right - 11 - valueW - 6 - left), left, textY, Widgets.label(this.setting.isDefault()), this.opacity);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (button != 0 || !this.contains(mouseX, mouseY) || this.opener == null) {
         return false;
      }

      this.opener.run();
      return true;
   }

   @Override
   public String hint() {
      return "click to edit the list";
   }
}
