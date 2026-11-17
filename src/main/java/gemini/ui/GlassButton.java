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
   private boolean primary;
   private boolean raised;
   private boolean leftAligned;
   private String detail;
   private int detailWidth;
   private IntSupplier badge;
   private int badgeColor = Glass.ERROR;
   /** Opacity (0..255) of a {@link #raised()} card's white fill at rest, the extra it gains when lit, and its edge. */
   private static final int RAISED_FILL = 12;
   private static final int RAISED_FILL_HOVER = 16;
   private static final int RAISED_EDGE = 16;

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

   /** The one main action of a screen: filled with the accent colour instead of the neutral card. */
   public GlassButton primary() {
      this.primary = true;
      return this;
   }

   /**
    * For a very dark backdrop (the home screen): the card is a faint lift of white with a hairline edge instead
    * of the dark surface colour, which would read as a hole there.
    */
   public GlassButton raised() {
      this.raised = true;
      return this;
   }

   /** Puts the label at the left edge instead of in the middle (for buttons stacked in a left-hand column). */
   public GlassButton leftAligned() {
      this.leftAligned = true;
      return this;
   }

   /** A short dim text on the right side of the card, e.g. the name of the world a "Continue" button opens. */
   public GlassButton detail(String text) {
      this.detail = text == null || text.isEmpty() ? null : text;
      this.detailWidth = -1;
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
         float lift = Math.min(1.0F, this.hover * 0.8F + press * 0.4F);
         if (this.primary) {
            int fill = Glass.lerp(Glass.lerp(accent, 0xFF000000, 0.25F), accent, lift);
            Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(fill, 230), opacity));
            if (this.isFocused()) {
               Glass.roundOutline(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, 150), opacity));
            }
         } else if (this.raised) {
            Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, Math.round(RAISED_FILL + RAISED_FILL_HOVER * lift)), opacity));
            int edge = Glass.lerp(Glass.withAlpha(0xFFFFFF, RAISED_EDGE), Glass.withAlpha(accent, 130), this.hover);
            Glass.roundOutline(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(edge, opacity));
         } else {
            int fill = Glass.lerp(Glass.surface(1), Glass.surface(3), lift);
            Glass.roundRect(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(fill, Math.round(150.0F + 70.0F * lift)), opacity));
            // No edge at rest; a thin accent outline marks only the hovered or focused button.
            if (this.hover > 0.02F) {
               Glass.roundOutline(g, x1, y1, x2, y2, Theme.RADIUS, Glass.fade(Glass.withAlpha(accent, Math.round(110 * this.hover)), opacity));
            }
         }
      }

      int base = this.plain
         ? Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, this.active ? this.hover : 0.0F)
         : (this.primary && this.active ? Glass.TEXT : Theme.labelColor(this.active, this.hover));
      int color = Glass.fade(base, opacity);
      int textY = y1 + (y2 - y1 - Theme.FONT_HEIGHT) / 2 + (this.plain ? 0 : 1);
      int pad = this.plain ? Theme.SPACE_XS : (this.leftAligned ? Theme.BUTTON_PAD : Theme.SPACE_M);
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

      if (this.detail != null && this.switchState == null) {
         if (this.detailWidth < 0) {
            this.detailWidth = font.width(this.detail);
         }

         int room = Math.max(0, (right - x1 - pad) - this.labelWidth() - Theme.SPACE_L);
         String shownDetail = this.detailWidth <= room ? this.detail : Theme.fit(this.detail, room, font::width);
         if (!shownDetail.isEmpty()) {
            int dw = shownDetail == this.detail ? this.detailWidth : font.width(shownDetail);
            g.text(font, shownDetail, right - dw, textY, Glass.fade(Glass.TEXT_FAINT, opacity), false);
            right -= dw + Theme.SPACE_L;
         }
      }

      if (this.switchState == null) {
         int left = x1 + pad;
         String shown = this.fit(font, right - left);
         int width = shown == this.label ? this.labelWidth() : font.width(shown);
         int centre = count > 0 ? (left + right) / 2 : (x1 + x2) / 2;
         g.text(font, shown, this.leftAligned ? left : centre - width / 2, textY, color, false);
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
