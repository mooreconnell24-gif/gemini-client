package gemini.ui.clickgui.controls;

import gemini.input.Keys;
import gemini.ui.Glass;
import gemini.ui.font.UiFont;
import java.util.function.IntPredicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

/**
 * A one-line inline text input for the menus: no widget tree, the owner draws it where it likes and forwards
 * keys while {@link #focused()}. Editing happens at the end of the text; right after {@link #focus} with
 * {@code selectAll} the whole text is selected, so the first typed character (or Backspace) replaces it.
 *
 * <p>Enter and Escape are deliberately not handled here: the owner decides what "apply" and "cancel" mean.
 */
public final class TextField {
   private final int maxLength;
   private final IntPredicate allowed;
   private String text = "";
   private boolean focused;
   private boolean allSelected;
   private float error;

   /**
    * @param maxLength most characters the field holds
    * @param allowed   which code points may be typed or pasted (null allows everything printable)
    */
   public TextField(int maxLength, IntPredicate allowed) {
      this.maxLength = Math.max(1, maxLength);
      this.allowed = allowed;
   }

   public String text() {
      return this.text;
   }

   public void setText(String value) {
      this.text = this.filter(value == null ? "" : value);
      this.allSelected = false;
   }

   public boolean focused() {
      return this.focused;
   }

   /** Starts editing with {@code initial} as the text; with {@code selectAll} typing replaces it. */
   public void focus(String initial, boolean selectAll) {
      this.text = this.filter(initial == null ? "" : initial);
      this.focused = true;
      this.allSelected = selectAll && !this.text.isEmpty();
   }

   public void blur() {
      this.focused = false;
      this.allSelected = false;
   }

   /** Tints the field with the error colour for a moment (bad input); it keeps the focus. */
   public void flashError() {
      this.error = 1.0F;
   }

   /** 0..1, how strongly the error flash is showing right now. */
   public float error() {
      return this.error;
   }

   /** Backspace, Delete (clears), Ctrl+A, Ctrl+C and Ctrl+V. Returns true when the key was used. */
   public boolean keyPressed(KeyEvent event) {
      if (!this.focused) {
         return false;
      }

      if (event.key() == Keys.BACKSPACE) {
         if (this.allSelected || event.hasControlDown()) {
            this.text = "";
         } else if (!this.text.isEmpty()) {
            this.text = this.text.substring(0, this.text.offsetByCodePoints(this.text.length(), -1));
         }

         this.allSelected = false;
         return true;
      } else if (event.key() == Keys.DELETE) {
         this.text = "";
         this.allSelected = false;
         return true;
      } else if (event.isSelectAll()) {
         this.allSelected = !this.text.isEmpty();
         return true;
      } else if (event.isCopy()) {
         Controls.setClipboard(this.text);
         return true;
      } else if (event.isPaste()) {
         String pasted = this.filter(Controls.clipboard().trim());
         this.text = this.filter(this.allSelected ? pasted : this.text + pasted);
         this.allSelected = false;
         return true;
      }

      return false;
   }

   public boolean charTyped(CharacterEvent event) {
      if (!this.focused) {
         return false;
      }

      int codepoint = event.codepoint();
      if (event.isAllowedChatCharacter() && (this.allowed == null || this.allowed.test(codepoint))) {
         if (this.allSelected) {
            this.text = "";
            this.allSelected = false;
         }

         if (this.text.codePointCount(0, this.text.length()) < this.maxLength) {
            this.text = this.text + event.codepointAsString();
         }
      }

      return true;
   }

   private String filter(String value) {
      StringBuilder out = new StringBuilder();
      int count = 0;
      for (int i = 0; i < value.length() && count < this.maxLength; ) {
         int codepoint = value.codePointAt(i);
         i += Character.charCount(codepoint);
         if (codepoint >= 32 && codepoint != 127 && (this.allowed == null || this.allowed.test(codepoint))) {
            out.appendCodePoint(codepoint);
            count++;
         }
      }

      return out.toString();
   }

   /**
    * Draws the field in the rectangle {@code x, y, w, h}. While unfocused it shows {@code idleText} (the live
    * value); focused it shows the edited text with a caret, right-aligned when {@code alignRight}.
    */
   public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String idleText, boolean alignRight, int accent, float opacity, float dt) {
      this.error = Math.max(0.0F, this.error - dt * 2.2F);
      int border = Glass.lerp(this.focused ? accent : Widgets.trackOff(), Glass.ERROR, this.error);
      Glass.roundRect(g, x, y, x + w, y + h, 3, Glass.fade(Glass.lerp(Widgets.well(), Glass.ERROR, this.error * 0.3F), opacity));
      Glass.roundOutline(g, x, y, x + w, y + h, 3, Glass.fade(Glass.withAlpha(border, this.focused || this.error > 0.0F ? 220 : 150), opacity));
      String shown = this.focused ? this.text : (idleText == null ? "" : idleText);
      boolean caret = this.focused && !this.allSelected && Util.getMillis() / 420L % 2L == 0L;
      int room = w - 8;
      while (shown.length() > 1 && UiFont.width(shown) > room) {
         shown = shown.substring(1);
      }

      int textW = UiFont.width(shown);
      int tx = alignRight ? x + w - 4 - textW : x + 4;
      int cap = UiFont.capHeight(gemini.ui.font.FontSize.BODY);
      int ty = UiFont.centerY(y, h);
      if (this.focused && this.allSelected && textW > 0) {
         g.fill(tx - 1, ty - 2, tx + textW + 1, ty + cap + 2, Glass.fade(Glass.withAlpha(accent, 110), opacity));
      }

      Controls.text(g, font, shown, tx, ty, Glass.lerp(this.focused ? Glass.text() : Glass.textDim(), Glass.ERROR, this.error), opacity);
      if (caret) {
         g.fill(tx + textW + 1, ty - 2, tx + textW + 2, ty + cap + 2, Glass.fade(Glass.text(), opacity));
      }
   }
}
