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
 * A vanilla button drawn as a Gemini glass card. Being a real {@link Button} it keeps vanilla focus handling
 * (Tab, arrow keys, Enter/Space), narration, tooltips and click sounds; only the look is replaced: the card
 * widens slightly and gains an accent glow while hovered or focused, flashes briefly when pressed, and fades to
 * {@link Theme#DISABLED_OPACITY} when inactive. Everything, the switch and the badge included, follows the
 * button's alpha, so the button fades with whatever contains it. The label is ellipsized to the card.
 *
 * <p>With a {@code switchState} the button is drawn as a labelled on/off switch instead. With a
 * {@link #badge(IntSupplier) badge} a count pill is shown on the right while the count is above zero.
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
   private int expand = 3;
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

      float emphasis = this.active ? Theme.emphasis(this.hover, this.pressedAt == 0L ? -1L : now - this.pressedAt) : 0.0F;
      int grow = Math.round(this.expand * this.hover);
      int x1 = this.getX() - grow;
      int x2 = this.getRight() + grow;
      int y1 = this.getY();
      int y2 = this.getBottom();
      int accent = Glass.accent();
      Glass.panel(g, x1, y1, x2, y2, Theme.RADIUS, Glass.surface(emphasis > 0.5F ? 2 : 0), accent, opacity, emphasis);
      // Accent tick on the left edge that lengthens with the hover animation.
      int tick = Math.max(1, Math.round((y2 - y1 - 8) * (0.35F + 0.65F * this.hover) / 2.0F));
      int middle = (y1 + y2) / 2;
      g.fill(x1 + 3, middle - tick, x1 + 4, middle + tick, Glass.fade(Glass.lerp(accent, Glass.accent2(), this.hover), opacity * (0.45F + 0.55F * this.hover)));
      Font font = Minecraft.getInstance().font;
      int color = Glass.fade(Theme.labelColor(this.active, this.hover), opacity);
      int textY = y1 + (y2 - y1 - Theme.FONT_HEIGHT) / 2;
      int right = x2 - Theme.SPACE_M;
      int count = this.badge == null ? 0 : this.badge.getAsInt();
      if (count > 0) {
         String text = count > 99 ? "99+" : Integer.toString(count);
         int bw = font.width(text) + 6;
         int by = y1 + (y2 - y1 - 10) / 2;
         Glass.roundRect(g, right - bw, by, right, by + 10, Theme.RADIUS_SMALL, Glass.fade(Glass.withAlpha(this.badgeColor, 190), opacity));
         g.text(font, text, right - bw + 3, by + 1, Glass.fade(Glass.TEXT, opacity), false);
         right -= bw + Theme.SPACE_S;
      }

      if (this.switchState == null) {
         int room = right - (x1 + Theme.SPACE_L);
         String shown = this.fit(font, room);
         int width = shown == this.label ? this.labelWidth() : font.width(shown);
         int centre = count > 0 ? (x1 + Theme.SPACE_L + right) / 2 : (x1 + x2) / 2;
         g.text(font, shown, centre - width / 2, textY, color, false);
      } else {
         boolean on = this.switchState.getAsBoolean();
         this.knob = Glass.approach(this.knob, on ? 1.0F : 0.0F, 12.0F, delta);
         int switchX = right - 18;
         g.text(font, this.fit(font, switchX - Theme.SPACE_S - (x1 + 9)), x1 + 9, textY, color, false);
         Glass.toggle(g, switchX, y1 + (y2 - y1 - 10) / 2, this.knob, on ? Glass.OK : accent, opacity);
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
