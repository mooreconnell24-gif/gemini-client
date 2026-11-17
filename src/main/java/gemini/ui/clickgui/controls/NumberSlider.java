package gemini.ui.clickgui.controls;

import gemini.input.Keys;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.UiMath;
import gemini.ui.font.UiFont;
import java.util.OptionalDouble;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Slider for a {@link NumberSetting}: a label, the value and a bar whose fill glides to the value.
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
   public static final int HEIGHT = 25;
   private static final int PAD = 10;
   private static final int FIELD_W = 46;
   private final NumberSetting setting;
   private final TextField field = new TextField(16, c -> c >= '0' && c <= '9' || c == '.' || c == ',' || c == '-' || c == '+');
   private float shown = Float.NaN;
   private float hover;
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
      return HEIGHT;
   }

   /** True while the value is being typed. */
   public boolean editing() {
      return this.field.focused();
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      Font font = Controls.font();
      int left = x + PAD;
      int right = x + w - PAD;
      int accent = this.accent();
      int accent2 = this.accent2();
      boolean over = mouseX != NO_MOUSE && inside(mouseX, mouseY, x, y, x + w, y + HEIGHT);
      this.hover = Glass.approach(this.hover, over || this.dragging ? 1.0F : 0.0F, 16.0F, dt);
      if (this.hover > 0.01F) {
         Glass.roundRect(g, x + 4, y, x + w - 4, y + HEIGHT - 1, 3, Glass.fade(Glass.withAlpha(0xFFFFFF, Math.round(18.0F * this.hover)), this.opacity));
      }

      int labelColor = this.setting.isDefault() ? Glass.textDim() : Glass.text();
      if (this.field.focused()) {
         Controls.text(g, font, Controls.fit(font, this.setting.name(), right - left - FIELD_W - 4), left, y + 3, labelColor, this.opacity);
         this.field.render(g, font, right - FIELD_W, y + 1, FIELD_W, 11, "", true, accent2, this.opacity, dt);
      } else {
         String value = this.setting.display();
         int valueW = UiFont.width(value);
         Controls.text(g, font, Controls.fit(font, this.setting.name(), right - left - valueW - 6), left, y + 3, labelColor, this.opacity);
         Controls.text(g, font, value, right - valueW, y + 3, Glass.lerp(accent2, 0xFFFFFFFF, this.hover * 0.4F), this.opacity);
      }

      float target = (float)Math.max(0.0, Math.min(1.0, this.setting.percent()));
      this.shown = Glass.approach(this.shown, target, 18.0F, dt);
      int trackY = y + 16;
      Glass.bar(g, left, trackY, right, trackY + 4, this.shown, Glass.fade(0xFF232A38, this.opacity), Glass.fade(accent, this.opacity), Glass.fade(accent2, this.opacity));
      int knob = left + Math.round((right - left) * this.shown);
      if (this.hover > 0.01F) {
         Glass.roundRect(g, knob - 4, trackY - 3, knob + 4, trackY + 7, 4, Glass.fade(Glass.withAlpha(accent2, Math.round(90.0F * this.hover)), this.opacity));
      }

      Glass.roundRect(g, knob - 2, trackY - 2, knob + 2, trackY + 6, 2, Glass.fade(0xFFFFFFFF, this.opacity));
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (!this.contains(mouseX, mouseY)) {
         return false;
      }

      if (this.field.focused()) {
         // a press on the field keeps typing; anywhere else on the row finishes the edit
         if (!inside(mouseX, mouseY, this.x + this.w - PAD - FIELD_W, this.y, this.x + this.w - PAD, this.y + 13)) {
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
