package gemini.ui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * A vanilla button drawn in Gemini's flat style, matching the buttons of the window menu. Being a real
 * {@link Button} it keeps vanilla focus handling (Tab, arrow keys, Enter/Space), narration, tooltips and click
 * sounds; only the look is replaced: a flat rounded card that lightens and gains a thin accent outline while
 * hovered or focused, and fades to {@link Theme#DISABLED_OPACITY} when inactive. Everything, the switch and the
 * badge included, follows the button's alpha, so the button fades with whatever contains it. The label is
 * ellipsized to the card.
 *
 * <p>With a {@code switchState} the button is drawn as a labelled on/off switch instead. With a
 * {@link #badge(IntSupplier, int) badge} a count pill is shown on the right while the count is above zero.
 * {@link #plain()} drops the card altogether for small secondary links: only the label is drawn, dim, and it
 * brightens while hovered or focused.
 */
public class GlassButton extends Button {
   private final BooleanSupplier switchState;
   private String label;
   private int labelWidth = -1;
   private String fitted;
   private int fittedFor = -1;
   private float hover;
   private float knob = Float.NaN;
   private long lastFrame;
   private long pressedAt;
   private int expand;
   private boolean plain;
   private IntSupplier badge;
   private int badgeColor = Glass.ERROR;

   public GlassButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
      this(x, y, width, height, message, onPress, null);
   }

   public GlassButton(int x, int y, int width, int height, Component message, Button.OnPress onPress, BooleanSupplier switchState) {
      super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
      this.switchState = switchState;
      this.label = message.getString();
   }

   /** How many pixels the card grows on each side while hovered; 0 for buttons packed tightly in a row. */
   public GlassButton expand(int pixels) {
      this.expand = Math.max(0, pixels);
      return this;
   }

   /** Draws only the label, without a card: for small secondary actions that should not compete with the main buttons. */
   public GlassButton plain() {
      this.plain = true;
      return this;
   }

   /** Shows {@code count} in a small pill on the right while it is above zero (e.g. key-bind conflicts). */
   public GlassButton badge(IntSupplier count, int color) {
      this.badge = count;
      this.badgeColor = color;
      return this;
   }

   /** Width a badge needs, for callers that size the button: a two-digit count plus padding and gap. */
   public static int badgeRoom(Font font) {
      return font.width("99") + 6 + Theme.SPACE_S;
   }

   @Override
   public void setMessage(Component message) {
      super.setMessage(message);
      this.label = message.getString();
      this.labelWidth = -1;
      this.fittedFor = -1;
   }

   @Override
   public void onPress(InputWithModifiers input) {
      this.pressedAt = Util.getMillis();
      super.onPress(input);
   }

   /** A switch narrates its state as well as its name. */
   @Override
   protected MutableComponent createNarrationMessage() {
      return this.switchState == null
         ? super.createNarrationMessage()
         : wrapDefaultNarrationMessage(Component.literal(this.label + (this.switchState.getAsBoolean() ? ": on" : ": off")));
   }

   @Override
   protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      boolean lit = this.active && this.isHoveredOrFocused();
      this.hover = Glass.approach(this.hover, lit ? 1.0F : 0.0F, Theme.HOVER_SPEED, delta);
      float opacity = Theme.controlOpacity(this.active, this.alpha);
      // Below this the card is invisible anyway, and text with next to no alpha is not reliably drawn faded.
      if (opacity <= 0.05F) {
         return;
      }

      Font font = Minecraft.getInstance().font;
      int grow = Math.round(this.expand * this.hover);
      int x1 = this.getX() - grow;
      int x2 = this.getRight() + grow;
      int y1 = this.getY();
      int y2 = this.getBottom();
      int accent = Glass.accent();
      if (!this.plain) {
         float press = this.pressedAt == 0L || now - this.pressedAt >= Theme.PRESS_MS ? 0.0F : 1.0F - (float)(now - this.pressedAt) / Theme.PRESS_MS;
         int fill = Glass.lerp(Glass.surface(1), Glass.surface(3), Math.min(1.0F, this.hover * 0.8F + press * 0.4F));
         Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(fill, 235), opacity));
         // A faint edge at rest; the accent outline only marks the hovered or focused button.
         int edge = Glass.lerp(Glass.withAlpha(0xFFFFFF, 18), Glass.withAlpha(accent, 120), this.hover);
         Glass.roundOutline(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(edge, opacity));
      }

      int color = Glass.fade(this.plain ? Glass.lerp(Glass.TEXT_FAINT, Glass.TEXT, this.active ? this.hover : 0.0F) : Theme.labelColor(this.active, this.hover), opacity);
      int textY = y1 + (y2 - y1 - Theme.FONT_HEIGHT) / 2 + (this.plain ? 0 : 1);
      int pad = this.plain ? Theme.SPACE_XS : Theme.SPACE_M;
      int right = x2 - pad;
      int count = this.badge == null ? 0 : this.badge.getAsInt();
      if (count > 0) {
         String text = count > 99 ? "99+" : Integer.toString(count);
         int bw = font.width(text) + 6;
         int by = y1 + (y2 - y1 - 10) / 2;
         Glass.roundRect(g, right - bw, by, right, by + 10, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(this.badgeColor, 150), opacity));
         g.text(font, text, right - bw + 3, by + 1, Glass.fade(Glass.TEXT, opacity), false);
         right -= bw + Theme.SPACE_S;
      }

      if (this.switchState == null) {
         int left = x1 + pad;
         String shown = this.fit(font, right - left);
         int width = shown == this.label ? this.labelWidth() : font.width(shown);
         int centre = count > 0 ? (left + right) / 2 : (x1 + x2) / 2;
         g.text(font, shown, centre - width / 2, textY, color, false);
      } else {
         boolean on = this.switchState.getAsBoolean();
         this.knob = Glass.approach(this.knob, on ? 1.0F : 0.0F, 12.0F, delta);
         int switchX = right - 18;
         int left = x1 + pad;
         g.text(font, this.fit(font, switchX - Theme.SPACE_S - left), left, textY, color, false);
         Glass.toggle(g, switchX, y1 + (y2 - y1 - 10) / 2, this.knob, accent, opacity);
      }
   }

   private int labelWidth() {
      if (this.labelWidth < 0) {
         this.labelWidth = Minecraft.getInstance().font.width(this.label);
      }

      return this.labelWidth;
   }

   /** The label ellipsized to {@code room}, cached per width (the card grows while hovered, so it changes). */
   private String fit(Font font, int room) {
      if (this.labelWidth() <= room) {
         return this.label;
      }

      if (this.fittedFor != room) {
         this.fittedFor = room;
         this.fitted = Theme.fit(this.label, room, font::width);
      }

      return this.fitted;
   }
}
