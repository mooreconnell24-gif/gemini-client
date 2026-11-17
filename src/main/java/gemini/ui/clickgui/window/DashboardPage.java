package gemini.ui.clickgui.window;

import gemini.Gemini;
import gemini.module.Module;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import gemini.ui.Theme;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.SettingControl;
import gemini.ui.clickgui.controls.TextField;
import gemini.ui.clickgui.controls.Widgets;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;

/**
 * One full-width page of the window menu (presets, keybinds, Baritone, mods). The page draws into the content area the
 * screen gives it and registers its clickable areas through {@link Host#hit}, the same draw-and-hit pass the module
 * list uses, so input always matches what is on screen. All coordinates are the screen's virtual (scaled)
 * coordinates.
 *
 * <p>Pages may host {@link TextField}s and {@link SettingControl}s. The screen calls {@link #blurExcept} before
 * every click, routes drags and releases to {@link #mouseDragged}/{@link #mouseReleased}, and forwards keys while
 * {@link #focused()}.
 */
public abstract class DashboardPage {
   protected static final int TONE_NEUTRAL = 0;
   protected static final int TONE_ACCENT = 1;
   protected static final int TONE_OK = 2;
   protected static final int TONE_DANGER = 3;
   protected final Host host;
   private final Map<Setting<?>, SettingControl> controls = new IdentityHashMap<>();
   private SettingControl activeControl;
   protected float scroll;
   protected float scrollTarget;
   protected int viewTop;
   protected int viewBottom;
   protected int viewX;
   protected int viewW;

   /** What a page needs from the screen hosting it. */
   public interface Host {
      Font font();

      /** The screen's open/close fade, 0..1, times the menu opacity where that applies. */
      float fade();

      /** Seconds since the last frame. */
      float dt();

      /**
       * Registers a clickable rectangle for this frame. {@code owner} identifies the text field or control the
       * area belongs to (null for plain buttons) so a click on it does not blur it first.
       */
      void hit(int x, int y, int w, int h, Object owner, Click click);

      /** A hit clipped to a scrolling viewport. */
      void hit(int x, int y, int w, int h, int clipTop, int clipBottom, Object owner, Click click);

      void tip(String text);

      /** A short message in the footer. */
      void notice(String text);

      /** Starts capturing the next key as {@code module}'s bind (Esc cancels, Delete clears). */
      void beginBind(Module module);

      /** The module whose bind is being captured, or null. */
      Module bindingModule();

      Screen screen();
   }

   @FunctionalInterface
   public interface Click {
      /** {@code button} is 0 left, 1 right, 2 middle; the position is in virtual coordinates. */
      void click(int button, double mouseX, double mouseY, boolean doubled);
   }

   protected DashboardPage(Host host) {
      this.host = host;
   }

   public abstract String title();

   /** Footer hint shown while nothing else is being said. */
   public abstract String hint();

   /** Called when the page is shown (again). */
   public void opened() {
   }

   public abstract void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY);

   // ---- input ----------------------------------------------------------------------------------------------

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (mouseX >= this.viewX && mouseX < this.viewX + this.viewW && mouseY >= this.viewTop && mouseY < this.viewBottom) {
         this.scrollTarget -= (float)amount;
         return true;
      }

      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      return this.activeControl != null && this.activeControl.mouseDragged(mouseX, mouseY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.activeControl == null) {
         return false;
      }

      SettingControl control = this.activeControl;
      this.activeControl = null;
      return control.mouseReleased(mouseX, mouseY, button);
   }

   /** True while a text field or control on this page owns the keyboard. */
   public boolean focused() {
      for (TextField field : this.fields()) {
         if (field.focused()) {
            return true;
         }
      }

      return this.focusedControl() != null;
   }

   /** Keys while {@link #focused()}: the focused control first, then {@link #fieldKey}. */
   public boolean keyPressed(KeyEvent event) {
      SettingControl control = this.focusedControl();
      if (control != null) {
         return control.keyPressed(event);
      }

      for (TextField field : this.fields()) {
         if (field.focused()) {
            this.fieldKey(field, event);
            return true;
         }
      }

      return false;
   }

   public boolean charTyped(CharacterEvent event) {
      SettingControl control = this.focusedControl();
      if (control != null) {
         return control.charTyped(event);
      }

      for (TextField field : this.fields()) {
         if (field.focused()) {
            return field.charTyped(event);
         }
      }

      return false;
   }

   /** Ends editing everywhere except in {@code owner} (the field or control the click landed on). */
   public void blurExcept(Object owner) {
      for (TextField field : this.fields()) {
         if (field != owner && field.focused()) {
            this.fieldBlurred(field);
            field.blur();
         }
      }

      for (SettingControl control : this.controls.values()) {
         if (control != owner) {
            control.blur();
         }
      }
   }

   /** The text fields of this page, so focus and keys can be handled generically. */
   protected TextField[] fields() {
      return new TextField[0];
   }

   /**
    * A key for a focused field. The default applies on Enter ({@link #fieldSubmitted}), cancels on Escape and
    * hands everything else to the field.
    */
   protected void fieldKey(TextField field, KeyEvent event) {
      if (gemini.input.Keys.isEnter(event.key())) {
         this.fieldSubmitted(field);
      } else if (event.key() == gemini.input.Keys.ESCAPE) {
         field.blur();
      } else {
         field.keyPressed(event);
      }
   }

   protected void fieldSubmitted(TextField field) {
      field.blur();
   }

   /** A field lost focus because the user clicked elsewhere. */
   protected void fieldBlurred(TextField field) {
   }

   // ---- setting controls -----------------------------------------------------------------------------------

   protected SettingControl control(Setting<?> setting) {
      SettingControl control = this.controls.get(setting);
      if (control == null) {
         control = SettingControl.of(setting);
         if (control == null) {
            return null;
         }

         control.setOnChange(DashboardPage::save);
         this.controls.put(setting, control);
      }

      return control;
   }

   /** Draws a control at the given place and makes it clickable; returns its height. */
   protected int drawControl(GuiGraphicsExtractor g, Setting<?> setting, int x, int y, int w, int mouseX, int mouseY, boolean clip) {
      SettingControl control = this.control(setting);
      if (control == null) {
         return 0;
      }

      boolean over = !clip || mouseY >= this.viewTop && mouseY < this.viewBottom;
      control.setOpacity(this.host.fade());
      control.render(g, x, y, w, over ? mouseX : SettingControl.NO_MOUSE, over ? mouseY : SettingControl.NO_MOUSE, this.host.dt());
      Click click = (button, mx, my, doubled) -> {
         if (button == 2) {
            control.blur();
            setting.reset();
            save();
         } else if (control.mouseClicked(mx, my, button, doubled)) {
            if (control.dragging()) {
               this.activeControl = control;
            }
         } else if (button == 1) {
            setting.reset();
            save();
         }
      };
      if (clip) {
         this.host.hit(x, y, w, control.height(), this.viewTop, this.viewBottom, control, click);
      } else {
         this.host.hit(x, y, w, control.height(), control, click);
      }

      return control.height();
   }

   private SettingControl focusedControl() {
      for (SettingControl control : this.controls.values()) {
         if (control.focused()) {
            return control;
         }
      }

      return null;
   }

   // ---- drawing helpers ------------------------------------------------------------------------------------

   protected Font font() {
      return this.host.font();
   }

   /** The colour faded with the screen. */
   protected int c(int color) {
      return Glass.fade(color, this.host.fade());
   }

   protected int c(int color, int alpha) {
      return Glass.fade(Glass.withAlpha(color, alpha), this.host.fade());
   }

   protected void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
      Controls.text(g, this.font(), text, x, y, color, this.host.fade());
   }

   protected void textFit(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
      if (maxWidth > 6) {
         this.text(g, Controls.fit(this.font(), text, maxWidth), x, y, color);
      }
   }

   /** The page title, in the same place and size as the Modules page's heading; returns its width. */
   protected int heading(GuiGraphicsExtractor g, int x, int y) {
      Controls.text(g, this.title(), x + 11, y + 7, Glass.text(), this.host.fade(), FontSize.LARGE, FontWeight.MEDIUM);
      return this.headingWidth();
   }

   protected int headingWidth() {
      return UiFont.width(this.title(), FontSize.LARGE, FontWeight.MEDIUM);
   }

   /** A flat rounded card like the module cards; {@code glow} (0..1) edges it in the accent. */
   protected void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float glow) {
      this.card(g, x1, y1, x2, y2, Glass.accent(), glow);
   }

   /** As {@link #card(GuiGraphicsExtractor, int, int, int, int, float)} with the edge in {@code edge} (a warning, say). */
   protected void card(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int edge, float glow) {
      Glass.roundRect(g, x1, y1, x2, y2, 6, this.c(Theme.mix(Glass.surface(2), edge, 0.08F * Mth.clamp(glow, 0.0F, 1.0F))));
      if (glow > 0.01F) {
         Glass.roundOutline(g, x1, y1, x2, y2, 6, this.c(edge, Math.round(150.0F * Mth.clamp(glow, 0.0F, 1.0F))));
      }
   }

   /** The menus' on/off switch at ({@code x}, {@code y}); 20 wide, 10 tall. */
   protected void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
      Widgets.toggle(g, x, y, on ? 1.0F : 0.0F, Glass.accent(), true, this.host.fade());
   }

   protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   /**
    * A rounded button. {@code disabledReason} non-null draws it greyed and makes a click explain why instead of
    * acting. Returns the button's width so rows of buttons can be laid out from left to right.
    */
   protected int button(
      GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY, int tone, String disabledReason, String tip, Click click
   ) {
      return this.button(g, x, y, w, h, label, mouseX, mouseY, tone, disabledReason, tip, false, click);
   }

   protected int button(
      GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mouseX, int mouseY, int tone, String disabledReason, String tip, boolean clip,
      Click click
   ) {
      boolean enabled = disabledReason == null;
      boolean over = inside(mouseX, mouseY, x, y, w, h) && (!clip || mouseY >= this.viewTop && mouseY < this.viewBottom);
      int base = switch (tone) {
         case TONE_ACCENT -> Glass.accent();
         case TONE_OK -> Theme.current().ok;
         case TONE_DANGER -> Theme.current().error;
         default -> Widgets.raised();
      };
      // Neutral buttons are raised chips; toned ones are the same chip washed with their colour.
      int rest = tone == TONE_NEUTRAL ? base : Theme.mix(Widgets.raised(), base, 0.42F);
      int lit = tone == TONE_NEUTRAL ? Widgets.raisedHover() : Theme.mix(Widgets.raised(), base, 0.68F);
      int fill = !enabled ? Glass.withAlpha(Glass.surface(3), 150) : (over ? lit : rest);
      Glass.roundRect(g, x, y, x + w, y + h, 4, this.c(fill));
      String shown = Controls.fit(this.font(), label, w - 6);
      // The same hover/disabled label rule as GlassButton and the panel rows.
      int color = !enabled ? Glass.textFaint() : (over || tone != TONE_NEUTRAL ? Glass.text() : Glass.lerp(Glass.textDim(), Glass.text(), 0.5F));
      this.text(g, shown, x + (w - UiFont.width(shown)) / 2, UiFont.centerY(y, h), color);
      Click action = enabled ? click : (b, mx, my, d) -> this.host.notice(disabledReason);
      if (clip) {
         this.host.hit(x, y, w, h, this.viewTop, this.viewBottom, null, action);
      } else {
         this.host.hit(x, y, w, h, null, action);
      }

      if (over) {
         this.host.tip(enabled ? tip : disabledReason);
      }

      return w;
   }

   /** Button width that fits its label. */
   protected int buttonWidth(String label) {
      return UiFont.width(label) + 14;
   }

   /** A small pill with text, e.g. a key name or a status; returns its width. */
   protected int chip(GuiGraphicsExtractor g, int x, int y, String label, int color, int textColor) {
      int w = UiFont.width(label) + 8;
      Glass.roundRect(g, x, y, x + w, y + 11, gemini.ui.Theme.RADIUS_SMALL, this.c(color));
      this.text(g, label, x + 4, UiFont.centerY(y, 11), textColor);
      return w;
   }

   /** Draws a text field and makes it clickable (focusing it, keeping its text). */
   protected void field(GuiGraphicsExtractor g, TextField field, int x, int y, int w, String placeholder, int mouseX, int mouseY, boolean clip) {
      boolean empty = field.text().isEmpty() && !field.focused();
      field.render(g, this.font(), x, y, w, 13, empty ? placeholder : field.text(), false, Glass.accent2(), this.host.fade(), this.host.dt());
      Click click = (button, mx, my, doubled) -> {
         if (button == 1) {
            field.focus("", false);
         } else if (!field.focused()) {
            field.focus(field.text(), doubled);
         }
      };
      if (clip) {
         this.host.hit(x, y, w, 13, this.viewTop, this.viewBottom, field, click);
      } else {
         this.host.hit(x, y, w, 13, field, click);
      }

      if (inside(mouseX, mouseY, x, y, w, 13)) {
         this.host.tip("Click to type  ·  Enter applies  ·  Esc cancels  ·  right-click clears");
      }
   }

   // ---- scrolling ------------------------------------------------------------------------------------------

   /** Sets the viewport and eases the scroll position; call before drawing the scrolled content. */
   protected void viewport(int x, int top, int w, int bottom, int contentHeight) {
      this.viewX = x;
      this.viewTop = top;
      this.viewW = w;
      this.viewBottom = bottom;
      float max = Math.max(0, contentHeight - (bottom - top));
      this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, max);
      this.scroll = Mth.clamp(Glass.approach(this.scroll, this.scrollTarget, 18.0F, this.host.dt()), 0.0F, max);
   }

   protected void scrollbar(GuiGraphicsExtractor g, int x, int contentHeight) {
      Widgets.scrollbar(g, x, this.viewTop, this.viewBottom - this.viewTop, contentHeight, this.scroll, this.host.fade());
   }

   // ---- shared ---------------------------------------------------------------------------------------------

   protected static Gemini gemini() {
      return Gemini.getInstance();
   }

   protected static void save() {
      if (Gemini.getInstance() != null && Gemini.getInstance().config() != null) {
         Gemini.getInstance().config().save();
      }
   }

   protected static String plain(String text) {
      return text == null ? "" : text.replaceAll("§.", "");
   }
}
