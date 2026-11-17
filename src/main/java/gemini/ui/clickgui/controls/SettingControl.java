package gemini.ui.clickgui.controls;

import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A self-contained editor for one setting, usable from any screen. The host owns layout and input routing; the
 * control owns everything inside its rectangle.
 *
 * <p>How a host uses one (see {@code CategoryPanel} for a complete example):
 * <ol>
 * <li>Create it once per setting ({@link #of(Setting)}) and keep it: it holds animation and edit state.</li>
 * <li>Each frame, reserve {@link #height()} pixels and call {@link #render}. The control remembers the rectangle
 * it was last drawn in, so the mouse methods take plain coordinates in that same coordinate space (apply the
 * host's own scaling to the mouse first). Pass {@link #NO_MOUSE} as the mouse position when the control is
 * covered or clipped and must not show hover.</li>
 * <li>Forward {@link #mouseClicked} when a press lands on it (buttons are 0 = left, 1 = right, 2 = middle, as
 * {@code Keys.glfwButton} returns them). While {@link #dragging()} forward {@link #mouseDragged} and
 * {@link #mouseReleased} wherever the cursor is.</li>
 * <li>While {@link #focused()} forward {@link #keyPressed} and {@link #charTyped} before the screen's own key
 * handling, and call {@link #blur()} when a press lands anywhere else.</li>
 * </ol>
 * Resetting to the default (middle click in both menus) is the host's business: just call
 * {@code setting().reset()}. {@link #setOnChange} is told whenever the control commits a value, which is the
 * moment to save the config.
 */
public abstract class SettingControl {
   /** Pass as mouseX/mouseY to {@link #render} when the control must not react to the cursor. */
   public static final int NO_MOUSE = Integer.MIN_VALUE;
   protected int x;
   protected int y;
   protected int w;
   protected float opacity = 1.0F;
   protected boolean compact;
   private int accent;
   private int accent2;
   private boolean customAccent;
   private Runnable onChange;

   /**
    * The control for a setting: {@link NumberSlider}, {@link ColorPicker}, {@link ToggleControl},
    * {@link ModeControl}, {@link ListControl} or {@link ActionControl}; null for a type that has none.
    */
   public static SettingControl of(Setting<?> setting) {
      if (setting instanceof NumberSetting number) {
         return new NumberSlider(number);
      } else if (setting instanceof ColorSetting color) {
         return new ColorPicker(color);
      } else if (setting instanceof BooleanSetting bool) {
         return new ToggleControl(bool);
      } else if (setting instanceof ModeSetting mode) {
         return new ModeControl(mode);
      } else if (setting instanceof ListSetting list) {
         return new ListControl(list);
      } else if (setting instanceof ActionSetting action) {
         return new ActionControl(action);
      } else {
         return null;
      }
   }

   /** Compact controls are a little shorter, for the narrow columns of the panel menu. */
   public void setCompact(boolean compact) {
      this.compact = compact;
   }

   /** Height of a one-line control. */
   protected final int rowHeight() {
      return this.compact ? 17 : 20;
   }

   /** The hint a host shows for this control next to the setting's description, or null. */
   public String hint() {
      return null;
   }

   public abstract Setting<?> setting();

   /** Height in pixels the control needs right now (it changes while a picker opens or closes). */
   public abstract int height();

   /**
    * Draws the control with its top-left corner at {@code x, y}, {@code w} pixels wide, and advances its
    * animations by {@code dt} seconds.
    */
   public abstract void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float dt);

   /** A press inside the control. Returns false when the control has no use for that button there. */
   public boolean mouseClicked(double mouseX, double mouseY, int button, boolean doubled) {
      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      return false;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return false;
   }

   /** Only meaningful while {@link #focused()}; returns true when the key was consumed. */
   public boolean keyPressed(KeyEvent event) {
      return false;
   }

   public boolean charTyped(CharacterEvent event) {
      return false;
   }

   /** True while a text input inside the control owns the keyboard. */
   public boolean focused() {
      return false;
   }

   /** True between a press that started a drag and its release. */
   public boolean dragging() {
      return false;
   }

   /** Ends text editing (applying the text when it is valid) because the user clicked elsewhere. */
   public void blur() {
   }

   /** Whether the point is inside the rectangle the control was last drawn in. */
   public boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseX < this.x + this.w && mouseY >= this.y && mouseY < this.y + this.height();
   }

   /** Fades the whole control (0..1), for hosts that animate their own opening. */
   public void setOpacity(float opacity) {
      this.opacity = Math.max(0.0F, Math.min(1.0F, opacity));
   }

   /** Overrides the theme accents (for hosts that tint per category). Without it the control follows {@link Glass}. */
   public void setAccent(int accent, int accent2) {
      this.accent = accent;
      this.accent2 = accent2;
      this.customAccent = true;
   }

   /** Called after every committed change (slider release, applied text, picked colour, sync button). */
   public void setOnChange(Runnable listener) {
      this.onChange = listener;
   }

   protected final int accent() {
      return this.customAccent ? this.accent : Glass.accent();
   }

   protected final int accent2() {
      return this.customAccent ? this.accent2 : Glass.accent2();
   }

   protected final void committed() {
      if (this.onChange != null) {
         this.onChange.run();
      }
   }

   protected final void place(int x, int y, int w) {
      this.x = x;
      this.y = y;
      this.w = w;
   }

   protected static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
      return mx >= x1 && mx < x2 && my >= y1 && my < y2;
   }
}
