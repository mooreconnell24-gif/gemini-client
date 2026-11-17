package gemini.ui.clickgui.controls;

import gemini.input.Keys;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;

/**
 * Picker for a {@link ColorSetting}. Collapsed it is one row with the name, the hex code and a swatch of the
 * live colour; a left click on that row opens (or closes) the picker underneath with an animated height:
 *
 * <ul>
 * <li>a saturation/brightness square for the current hue,</li>
 * <li>a hue slider and an alpha slider (over a checkerboard),</li>
 * <li>a hex input ("#RGB", "#RRGGBB" or "#AARRGGBB"; Enter applies, Escape cancels, bad text flashes red),</li>
 * <li>Copy and Paste buttons for the system clipboard,</li>
 * <li>Static / Rainbow / Pulse sync buttons.</li>
 * </ul>
 * The square is drawn as at most {@value #MAX_COLUMNS} vertical gradient strips, so the whole picker costs
 * around a hundred quads however large it is.
 */
public final class ColorPicker extends SettingControl {
   /** Height of the collapsed row. */
   public static final int ROW = 20;
   private static final int MAX_COLUMNS = 32;
   private static final int PAD = Widgets.PAD;
   private static final int SQUARE_H = 56;
   private static final int BAR_H = 8;
   private static final int LINE_H = 13;
   private static final int BODY = 2 + SQUARE_H + 4 + BAR_H + 4 + BAR_H + 5 + LINE_H + 3 + LINE_H + 3 + LINE_H + 4;
   private static final String[] SYNC = {ColorSetting.STATIC, ColorSetting.RAINBOW, ColorSetting.PULSE};
   private static final int DRAG_NONE = 0;
   private static final int DRAG_SQUARE = 1;
   private static final int DRAG_HUE = 2;
   private static final int DRAG_ALPHA = 3;
   private final ColorSetting setting;
   private final TextField hex = new TextField(9, c -> c == '#' || Character.digit(c, 16) >= 0);
   private boolean expanded;
   private float expand;
   private float hover;
   private int drag = DRAG_NONE;
   private String notice;
   private long noticeUntil;

   public ColorPicker(ColorSetting setting) {
      this.setting = setting;
   }

   @Override
   public Setting<?> setting() {
      return this.setting;
   }

   public boolean expanded() {
      return this.expanded;
   }

   public void setExpanded(boolean expanded) {
      this.expanded = expanded;
      if (!expanded) {
         this.blur();
         this.drag = DRAG_NONE;
      }
   }

   @Override
   public int height() {
      return this.rowHeight() + this.bodyShown();
   }

   private int bodyShown() {
      return Math.round(BODY * Glass.easeInOut(this.expand));
   }

   // ---- geometry (all relative to the last render position) ------------------------------------------------

   private int left() {
      return this.x + PAD;
   }

   private int right() {
      return this.x + this.w - PAD;
   }

   private int squareTop() {
      return this.y + this.rowHeight() + 2;
   }

   private int hueTop() {
      return this.squareTop() + SQUARE_H + 4;
   }

   private int alphaTop() {
      return this.hueTop() + BAR_H + 4;
   }

   private int hexTop() {
      return this.alphaTop() + BAR_H + 5;
   }

   private int buttonsTop() {
      return this.hexTop() + LINE_H + 3;
   }

   private int syncTop() {
      return this.buttonsTop() + LINE_H + 3;
   }

   @Override
   public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt) {
      this.place(x, y, w);
      this.expand = Glass.approach(this.expand, this.expanded ? 1.0F : 0.0F, 14.0F, dt);
      Font font = Controls.font();
      int row = this.rowHeight();
      int left = this.left();
      int right = this.right();
      int accent = this.accent();
      int accent2 = this.accent2();
      boolean hasMouse = mouseX != NO_MOUSE;
      boolean overRow = hasMouse && inside(mouseX, mouseY, x, y, x + w, y + row);
      this.hover = Glass.approach(this.hover, overRow ? 1.0F : 0.0F, 16.0F, dt);
      Widgets.rowHover(g, x, y, w, this.height(), Math.max(this.hover, this.expand * 0.6F), this.opacity);

      // collapsed row: name, hex, swatch
      int swatchL = right - 16;
      int swatchT = y + (row - 10) / 2;
      String code = this.setting.hex();
      int codeW = UiFont.width(code, FontSize.SMALL, FontWeight.REGULAR);
      int nameRoom = swatchL - 5 - left;
      boolean showCode = nameRoom - codeW - 6 >= UiFont.width(this.setting.name());
      int textY = UiFont.centerY(y, row);
      Controls.text(g, font, Controls.fit(font, this.setting.name(), showCode ? nameRoom - codeW - 6 : nameRoom), left, textY, Widgets.label(this.setting.isDefault()), this.opacity);
      if (showCode) {
         Controls.text(g, code, swatchL - 5 - codeW, UiFont.centerY(y, row, FontSize.SMALL), Glass.textFaint(), this.opacity, FontSize.SMALL, FontWeight.REGULAR);
      }

      int live = this.setting.argb();
      if (live >>> 24 < 250) {
         Controls.checker(g, swatchL + 1, swatchT + 1, right - 1, swatchT + 9, 4, this.opacity);
      }

      Glass.roundRect(g, swatchL, swatchT, right, swatchT + 10, 3, Glass.fade(live, this.opacity));
      Glass.roundOutline(g, swatchL, swatchT, right, swatchT + 10, 3, Glass.fade(Glass.withAlpha(Glass.lerp(Glass.text(), accent, this.expand), 60 + Math.round(90.0F * Math.max(this.hover, this.expand))), this.opacity));

      int shown = this.bodyShown();
      if (shown <= 0) {
         return;
      }

      g.enableScissor(x, y + this.rowHeight(), x + w, y + this.rowHeight() + shown);
      float fade = this.opacity * this.expand;
      this.drawSquare(g, left, this.squareTop(), right, fade);
      this.drawHue(g, left, this.hueTop(), right, fade);
      this.drawAlpha(g, left, this.alphaTop(), right, fade);
      this.hex.render(g, font, left, this.hexTop(), right - left, LINE_H, this.hexLabel(), false, accent2, fade, dt);
      int half = (right - left - 3) / 2;
      this.button(g, font, left, this.buttonsTop(), half, "Copy", hasMouse, mouseX, mouseY, false, accent, fade);
      this.button(g, font, right - half, this.buttonsTop(), half, "Paste", hasMouse, mouseX, mouseY, false, accent, fade);
      int[] bounds = this.syncBounds(font);
      for (int i = 0; i < SYNC.length; i++) {
         this.button(g, font, bounds[i], this.syncTop(), bounds[i + 1] - bounds[i] - 2, SYNC[i], hasMouse, mouseX, mouseY, SYNC[i].equals(this.setting.sync()), accent, fade);
      }

      g.disableScissor();
   }

   private String hexLabel() {
      return this.notice != null && Util.getMillis() < this.noticeUntil ? this.notice : this.setting.hex();
   }

   /** Saturation runs left to right, brightness top to bottom: one vertical gradient strip per column. */
   private void drawSquare(GuiGraphicsExtractor g, int left, int top, int right, float fade) {
      int width = right - left;
      int columns = Math.max(4, Math.min(MAX_COLUMNS, width / 3));
      float hue = this.setting.hue();
      int bottom = Glass.fade(0xFF000000, fade);
      for (int i = 0; i < columns; i++) {
         int sx = left + width * i / columns;
         int ex = left + width * (i + 1) / columns;
         float saturation = columns <= 1 ? 1.0F : (float)i / (columns - 1);
         g.fillGradient(sx, top, ex, top + SQUARE_H, Glass.fade(Glass.hsb(hue, saturation, 1.0F), fade), bottom);
      }

      Glass.roundOutline(g, left - 1, top - 1, right + 1, top + SQUARE_H + 1, 2, Glass.fade(Glass.withAlpha(Glass.text(), 70), fade));
      int cx = left + Math.round(width * this.setting.saturation());
      int cy = top + Math.round(SQUARE_H * (1.0F - this.setting.brightness()));
      g.enableScissor(left - 1, top - 1, right + 1, top + SQUARE_H + 1);
      g.outline(cx - 3, cy - 3, 6, 6, Glass.fade(0xFF000000, fade));
      g.outline(cx - 2, cy - 2, 4, 4, Glass.fade(0xFFFFFFFF, fade));
      g.disableScissor();
   }

   private void drawHue(GuiGraphicsExtractor g, int left, int top, int right, float fade) {
      int width = right - left;
      int segments = Math.max(6, Math.min(36, width / 3));
      for (int i = 0; i < segments; i++) {
         int sx = left + width * i / segments;
         int ex = left + width * (i + 1) / segments;
         g.fill(sx, top, ex, top + BAR_H, Glass.fade(Glass.hsb((i + 0.5F) / segments, 1.0F, 1.0F), fade));
      }

      this.knob(g, left + Math.round(width * this.setting.hue()), top, fade);
   }

   private void drawAlpha(GuiGraphicsExtractor g, int left, int top, int right, float fade) {
      Controls.checker(g, left, top, right, top + BAR_H, 4, fade);
      int rgb = this.setting.baseArgb() & 0xFFFFFF;
      Glass.gradientBar(g, left, top, right, top + BAR_H, Glass.fade(rgb, 0.0F), Glass.fade(0xFF000000 | rgb, fade));
      this.knob(g, left + Math.round((right - left) * this.setting.alpha()), top, fade);
   }

   private void knob(GuiGraphicsExtractor g, int cx, int top, float fade) {
      g.fill(cx - 2, top - 1, cx + 2, top + BAR_H + 1, Glass.fade(0xFF10141C, fade));
      g.fill(cx - 1, top - 1, cx + 1, top + BAR_H + 1, Glass.fade(0xFFFFFFFF, fade));
   }

   private void button(
      GuiGraphicsExtractor g, Font font, int bx, int by, int bw, String label, boolean hasMouse, int mouseX, int mouseY, boolean active, int accent, float fade
   ) {
      boolean over = hasMouse && inside(mouseX, mouseY, bx, by, bx + bw, by + LINE_H);
      int fill = active ? Glass.lerp(Widgets.raised(), accent, 0.55F) : (over ? Widgets.raisedHover() : Widgets.raised());
      Glass.roundRect(g, bx, by, bx + bw, by + LINE_H, 3, Glass.fade(fill, fade));
      String text = Controls.fit(font, label, bw - 2);
      Controls.text(g, font, text, bx + (bw - UiFont.width(text)) / 2, UiFont.centerY(by, LINE_H), active || over ? Glass.text() : Glass.textDim(), fade);
   }

   /** Left edges of the three sync buttons plus the right end, sized by their labels. */
   private int[] syncBounds(Font font) {
      int left = this.x + 6;
      int total = this.w - 12;
      int[] widths = new int[SYNC.length];
      int textTotal = 0;
      for (int i = 0; i < SYNC.length; i++) {
         widths[i] = UiFont.width(SYNC[i]);
         textTotal += widths[i];
      }

      int[] bounds = new int[SYNC.length + 1];
      int spare = total - textTotal;
      int cursor = left;
      for (int i = 0; i < SYNC.length; i++) {
         bounds[i] = cursor;
         cursor += widths[i] + spare / SYNC.length;
      }

      bounds[SYNC.length] = left + total + 2;
      return bounds;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      if (!this.contains(mouseX, mouseY)) {
         return false;
      }

      if (mouseY < this.y + this.rowHeight()) {
         if (button != 0) {
            return false;
         }

         this.setExpanded(!this.expanded);
         return true;
      }

      if (button != 0 || !this.expanded) {
         return true;
      }

      int left = this.left();
      int right = this.right();
      boolean onHex = inside(mouseX, mouseY, left, this.hexTop(), right, this.hexTop() + LINE_H);
      if (this.hex.focused() && !onHex) {
         this.blur();
      }

      if (inside(mouseX, mouseY, left - 2, this.squareTop() - 1, right + 2, this.squareTop() + SQUARE_H + 1)) {
         this.drag = DRAG_SQUARE;
         this.dragTo(mouseX, mouseY);
      } else if (inside(mouseX, mouseY, left - 2, this.hueTop() - 2, right + 2, this.hueTop() + BAR_H + 2)) {
         this.drag = DRAG_HUE;
         this.dragTo(mouseX, mouseY);
      } else if (inside(mouseX, mouseY, left - 2, this.alphaTop() - 2, right + 2, this.alphaTop() + BAR_H + 2)) {
         this.drag = DRAG_ALPHA;
         this.dragTo(mouseX, mouseY);
      } else if (onHex) {
         if (!this.hex.focused()) {
            this.notice = null;
            this.hex.focus(this.setting.hex(), true);
         }
      } else if (inside(mouseX, mouseY, left, this.buttonsTop(), right, this.buttonsTop() + LINE_H)) {
         int half = (right - left - 3) / 2;
         if (mouseX < left + half) {
            Controls.setClipboard(this.setting.hex());
            this.notify("Copied " + this.setting.hex());
         } else if (mouseX >= right - half) {
            if (this.setting.setHex(Controls.clipboard())) {
               this.notify("Pasted " + this.setting.hex());
               this.committed();
            } else {
               this.notify("Not a colour");
               this.hex.flashError();
            }
         }
      } else if (mouseY >= this.syncTop() && mouseY < this.syncTop() + LINE_H) {
         int[] bounds = this.syncBounds(Controls.font());
         for (int i = 0; i < SYNC.length; i++) {
            if (mouseX >= bounds[i] && mouseX < bounds[i + 1] - 2) {
               this.setting.setSync(SYNC[i]);
               this.committed();
               break;
            }
         }
      }

      return true;
   }

   private void notify(String text) {
      this.notice = text;
      this.noticeUntil = Util.getMillis() + 1400L;
   }

   private void dragTo(double mouseX, double mouseY) {
      float across = (float)Math.max(0.0, Math.min(1.0, (mouseX - this.left()) / Math.max(1, this.right() - this.left())));
      switch (this.drag) {
         case DRAG_SQUARE -> {
            this.setting.setSaturation(across);
            this.setting.setBrightness(1.0F - (float)Math.max(0.0, Math.min(1.0, (mouseY - this.squareTop()) / SQUARE_H)));
         }
         case DRAG_HUE -> this.setting.set(Math.round(across * 360.0F));
         case DRAG_ALPHA -> this.setting.setAlpha(across);
         default -> {
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (this.drag == DRAG_NONE || button != 0) {
         return false;
      }

      this.dragTo(mouseX, mouseY);
      return true;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.drag == DRAG_NONE) {
         return false;
      }

      this.drag = DRAG_NONE;
      this.committed();
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (!this.hex.focused()) {
         return false;
      }

      if (Keys.isEnter(event.key())) {
         if (this.setting.setHex(this.hex.text())) {
            this.hex.blur();
            this.committed();
         } else {
            this.hex.flashError();
         }
      } else if (event.key() == Keys.ESCAPE) {
         this.hex.blur();
      } else {
         this.hex.keyPressed(event);
      }

      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      return this.hex.focused() && this.hex.charTyped(event);
   }

   @Override
   public String hint() {
      return "click to open the picker";
   }

   @Override
   public boolean focused() {
      return this.hex.focused();
   }

   @Override
   public boolean dragging() {
      return this.drag != DRAG_NONE;
   }

   @Override
   public void blur() {
      if (this.hex.focused()) {
         if (this.setting.setHex(this.hex.text())) {
            this.committed();
         }

         this.hex.blur();
      }
   }
}
