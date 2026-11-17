package gemini.ui.clickgui.controls;

import gemini.input.Keys;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.Theme;
import gemini.ui.UiMath;
import gemini.ui.font.UiFont;
import java.util.OptionalDouble;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Slider for a {@link NumberSetting}: the name, the value in a small bubble and a thin track whose fill glides to
 * the value. The bubble lights up in the accent while the slider is dragged.
 *
 * <ul>
 * <li>Left-drag moves the knob to the cursor. Holding Shift makes the drag relative and ten times slower, for
 * fine adjustment; pressing with Shift held never jumps.</li>
 * <li>Double-click or right-click turns the value into a text input. Enter applies it through
 * {@link NumberSetting#set} (which clamps and steps it), Escape cancels, and text that is not a number flashes
 * the error colour and stays in edit mode. Clicking elsewhere ({@link #blur()}) applies a valid number and
 * drops an invalid one.</li>
 * </ul>
 */
public final class NumberSlider extends SettingControl {
   public static final int HEIGHT = 26;
   private static final int PAD = Widgets.PAD;
   private static final int FIELD_W = 46;
   private final NumberSetting setting;
   private final TextField field = new TextField(16, c -> c >= '0' && c <= '9' || c == '.' || c == ',' || c == '-' || c == '+');
   private float shown = Float.NaN;
   private final Glass.Hover hover = new Glass.Hover();
   private boolean dragging;
   private double dragPercent;
   private double lastMouseX;
   private double valueBeforePress;

   public NumberSlider(NumberSetting setting) {
      this.setting = setting;
   }

   @Override
   public Setting<?> setting() {
      return this.setting;
   }

   @Override
   public int height() {
      return this.compact ? HEIGHT - 3 : HEIGHT;
   }

   /** True while the value is being typed. */
   public boolean editing() {
      return this.field.focused();
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      int h = this.height();
      int left = x + PAD;
      int right = x + w - PAD;
      int accent = this.accent();
      int accent2 = this.accent2();
      boolean over = mouseX != NO_MOUSE && inside(mouseX, mouseY, x, y, x + w, y + h);
      this.hover.update(over || this.dragging, this.dragging, dt);
      float lit = this.hover.hover;
      Widgets.rowHover(g, x, y, w, h, lit, this.opacity);
      int labelColor = Widgets.label(this.setting.isDefault());
      int textY = y + (this.compact ? 3 : 4);
      if (this.field.focused()) {
         Controls.text(g, null, UiFont.ellipsize(this.setting.name(), right - left - FIELD_W - 4), left, textY, labelColor, this.opacity);
         this.field.render(g, null, right - FIELD_W, textY - 3, FIELD_W, 13, "", true, accent, this.opacity, dt);
      } else {
         // the value sits in a small bubble that lights up in the accent while the slider is dragged
         String value = this.setting.display();
         float press = this.hover.press;
         int fill = Glass.lerp(Glass.lerp(Widgets.raised(), Widgets.raisedHover(), lit), accent, press);
         int ink = Glass.lerp(Glass.lerp(Glass.textDim(), Glass.text(), lit), Theme.onColor(accent), press);
         int bubbleL = Widgets.chip(g, value, right, textY - 2, 11, fill, ink, this.opacity);
         Controls.text(g, null, UiFont.ellipsize(this.setting.name(), bubbleL - left - 5), left, textY, labelColor, this.opacity);
      }

      float target = (float)Math.max(0.0, Math.min(1.0, this.setting.percent()));
      this.shown = Float.isNaN(this.shown) ? target : Glass.approach(this.shown, target, 22.0F, dt);
      int trackY = y + h - 7;
      Glass.roundRect(g, left, trackY, right, trackY + 2, 1, Glass.fade(Widgets.trackOff(), this.opacity));
      int knob = left + Math.round((right - left) * this.shown);
      if (knob > left) {
         Glass.roundGradient(g, left, trackY, Math.max(left + 2, knob), trackY + 2, 1, Glass.fade(accent, this.opacity), Glass.fade(Glass.lerp(accent, accent2, this.shown), this.opacity));
      }

      int cx = Math.max(left + 3, Math.min(right - 3, knob));
      if (lit > 0.01F) {
         Glass.roundRect(g, cx - 6, trackY - 5, cx + 6, trackY + 7, 6, Glass.fade(Glass.withAlpha(accent, Math.round(56.0F * lit)), this.opacity));
      }

      Glass.roundRect(g, cx - 3, trackY - 2, cx + 3, trackY + 4, 3, Glass.fade(Glass.lerp(Glass.text(), 0xFFFFFFFF, lit), this.opacity));
   }

   @Override
   public String hint() {
      return "Shift-drag = fine  ·  double-click or right-click to type";
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (!this.contains(mouseX, mouseY)) {
         return false;
      }

      if (this.field.focused()) {
         // a press on the field keeps typing; anywhere else on the row finishes the edit
         if (!inside(mouseX, mouseY, this.x + this.w - PAD - FIELD_W, this.y, this.x + this.w - PAD, this.y + 15)) {
            this.blur();
         }

         return true;
      }

      if (button == 1 || button == 0 && doubled) {
         if (button == 0) {
            // the first press of the double-click moved the knob: put the value back before editing it
            this.setting.set(this.valueBeforePress);
         }

         this.dragging = false;
         this.field.focus(this.setting.display(), true);
         return true;
      } else if (button == 0) {
         this.dragging = true;
         this.valueBeforePress = this.setting.get();
         this.lastMouseX = mouseX;
         this.dragPercent = this.setting.percent();
         if (!Controls.shiftDown()) {
            this.dragPercent = UiMath.sliderDrag(this.dragPercent, mouseX, mouseX, this.x + PAD, this.w - PAD * 2, false);
            this.setting.setFromPercent(this.dragPercent);
         }

         return true;
      }

      return false;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (!this.dragging || button != 0) {
         return false;
      }

      this.dragPercent = UiMath.sliderDrag(this.dragPercent, this.lastMouseX, mouseX, this.x + PAD, this.w - PAD * 2, Controls.shiftDown());
      this.lastMouseX = mouseX;
      this.setting.setFromPercent(this.dragPercent);
      return true;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (!this.dragging) {
         return false;
      }

      this.dragging = false;
      this.committed();
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (!this.field.focused()) {
         return false;
      }

      if (Keys.isEnter(event.key())) {
         this.apply(true);
      } else if (event.key() == Keys.ESCAPE) {
         this.field.blur();
      } else {
         this.field.keyPressed(event);
      }

      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      return this.field.focused() && this.field.charTyped(event);
   }

   @Override
   public boolean focused() {
      return this.field.focused();
   }

   @Override
   public boolean dragging() {
      return this.dragging;
   }

   @Override
   public void blur() {
      if (this.field.focused()) {
         this.apply(false);
         this.field.blur();
      }
   }

   /** Applies the typed text. A bad number flashes and (when {@code keepEditing}) leaves the field open. */
   private void apply(boolean keepEditing) {
      OptionalDouble parsed = UiMath.parseNumber(this.field.text());
      if (parsed.isPresent()) {
         this.setting.set(parsed.getAsDouble());
         this.field.blur();
         this.committed();
      } else if (keepEditing) {
         this.field.flashError();
      }
   }
}
