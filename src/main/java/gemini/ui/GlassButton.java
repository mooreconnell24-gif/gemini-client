package gemini.ui;

import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * A vanilla button drawn in Gemini's style. Being a real {@link Button} it keeps vanilla focus handling (Tab,
 * arrow keys, Enter/Space), narration, tooltips and click sounds; only the look is replaced: a rounded card with a
 * hairline edge that lightens, and whose edge turns to the accent, while hovered or focused (eased, see
 * {@link Glass.Hover}), and that fades to {@link Theme#DISABLED_OPACITY} when inactive. Everything, the switch and the
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
   private boolean segment;
   private boolean stacked;
   private boolean iconOnly;
   private GlassIcon icon;
   private int radius = Theme.RADIUS;
   private int tint;
   private float slideY;
   private final Glass.Hover state = new Glass.Hover();
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
      this.labelWidth = -1;
      this.fittedFor = -1;
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

   /** Corner radius of the card, for screens that use larger, softer buttons. */
   public GlassButton radius(int radius) {
      this.radius = Math.max(0, radius);
      return this;
   }

   /** One entry of a bar (the pause-menu bar): no card at rest, a soft rounded fill under the mouse. */
   public GlassButton segment() {
      this.segment = true;
      return this;
   }

   /** Colours a {@link #segment()} or {@link #plain()} button's hover state, e.g. red for a destructive action. 0 = none. */
   public GlassButton tint(int color) {
      this.tint = color;
      return this;
   }

   /** Draws {@code icon} left of the label. */
   public GlassButton icon(GlassIcon icon) {
      this.icon = icon;
      return this;
   }

   /** With an {@link #icon(GlassIcon) icon}: a tile with the icon above a small caption instead of beside the label. */
   public GlassButton stacked() {
      this.stacked = true;
      return this;
   }

   /** Draws only the {@link #icon(GlassIcon) icon}; the label is still what the narrator reads. */
   public GlassButton iconOnly() {
      this.iconOnly = true;
      return this;
   }

   /** Vertical offset in GUI units for this frame's drawing only (an entrance slide); the hit box does not move. */
   public void setSlide(float offsetY) {
      this.slideY = offsetY;
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
      return UiFont.width("99") + 6 + Theme.SPACE_S;
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
      boolean pressed = this.pressedAt != 0L && now - this.pressedAt < Theme.PRESS_MS;
      this.state.update(lit, lit && pressed, delta);
      this.hover = this.state.hover;
      float opacity = Theme.controlOpacity(this.active, this.alpha);
      // Below this the card is invisible anyway, and text with next to no alpha is not reliably drawn faded.
      if (opacity <= 0.05F) {
         return;
      }

      boolean moved = this.slideY != 0.0F;
      if (moved) {
         g.pose().pushMatrix();
         g.pose().translate(0.0F, this.slideY);
      }

      this.extractCard(g, opacity);
      this.extractLabel(g, delta, opacity);
      if (moved) {
         g.pose().popMatrix();
      }
   }

   /** The card behind the label, in whichever style the button has. */
   private void extractCard(GuiGraphicsExtractor g, float opacity) {
      if (this.plain) {
         return;
      }

      int grow = Math.round(this.expand * this.hover);
      int x1 = this.getX() - grow;
      int x2 = this.getRight() + grow;
      int y1 = this.getY();
      int y2 = this.getBottom();
      int r = this.radius;
      int accent = Glass.accent();
      float lift = this.state.emphasis();
      if (this.primary) {
         // The one accent-filled control: a soft left-to-right blend of the theme's two accents, a little
         // brighter and with a faint accent halo while hovered.
         int from = Glass.lerp(Glass.lerp(accent, 0xFF000000, 0.14F), accent, lift);
         int to = Glass.lerp(from, Glass.accent2(), 0.45F);
         if (this.active && this.hover > 0.02F) {
            Glass.shadow(g, x1, y1, x2, y2, r, 10, 2, Glass.fade(Glass.withAlpha(accent, Math.round(70.0F * this.hover)), opacity));
         }

         Glass.roundGradient(g, x1, y1, x2, y2, r, Glass.fade(from, opacity), Glass.fade(to, opacity));
         if (this.isFocused()) {
            Glass.roundOutline(g, x1, y1, x2, y2, r, Glass.fade(Glass.withAlpha(0xFFFFFF, 150), opacity));
         }
      } else if (this.segment) {
         // Part of a bar: nothing at rest, a soft fill under the mouse.
         int tint = this.tint != 0 ? this.tint : 0xFFFFFFFF;
         int alpha = Math.round((this.tint != 0 ? 46.0F : 22.0F) * Math.min(1.0F, lift * 1.25F));
         if (alpha > 0) {
            Glass.roundRect(g, x1, y1, x2, y2, r, Glass.fade(Glass.withAlpha(tint, alpha), opacity));
         }
      } else if (this.raised) {
         Glass.roundRect(g, x1, y1, x2, y2, r, Glass.fade(Glass.withAlpha(0xFFFFFF, Math.round(RAISED_FILL + RAISED_FILL_HOVER * lift)), opacity));
         int edge = Glass.lerp(Glass.withAlpha(0xFFFFFF, RAISED_EDGE), Glass.withAlpha(accent, 120), this.hover);
         Glass.roundOutline(g, x1, y1, x2, y2, r, Glass.fade(edge, opacity));
      } else {
         int fill = Glass.lerp(Glass.surface(2), Glass.surface(3), lift);
         Glass.roundRect(g, x1, y1, x2, y2, r, Glass.fade(Glass.withAlpha(fill, Math.round(200.0F + 40.0F * lift)), opacity));
         int edge = Glass.lerp(Glass.withAlpha(0xFFFFFF, 14), Glass.withAlpha(accent, 120), this.hover);
         Glass.roundOutline(g, x1, y1, x2, y2, r, Glass.fade(edge, opacity));
      }
   }

   private void extractLabel(GuiGraphicsExtractor g, float delta, float opacity) {
      int grow = this.plain ? 0 : Math.round(this.expand * this.hover);
      int x1 = this.getX() - grow;
      int x2 = this.getRight() + grow;
      int y1 = this.getY();
      int y2 = this.getBottom();
      int accent = Glass.accent();
      float lit = this.active ? this.hover : 0.0F;
      int base;
      if (this.primary && this.active) {
         // White where it is readable on the accent (large text needs 3:1), otherwise the dark tone.
         int fill = Glass.lerp(accent, Glass.accent2(), 0.25F);
         base = Theme.contrast(0xFFFFFFFF, accent) >= 3.0 ? 0xFFFFFFFF : Theme.onColor(fill);
      } else if (this.plain || this.segment) {
         base = Glass.lerp(Glass.textDim(), this.tint != 0 ? Glass.lerp(this.tint, Glass.text(), 0.35F) : Glass.text(), lit);
      } else {
         base = Theme.labelColor(this.active, this.hover);
      }

      int color = Glass.fade(base, opacity);
      FontWeight weight = this.primary ? FontWeight.MEDIUM : FontWeight.REGULAR;
      if (this.stacked && this.icon != null) {
         // A tile: the icon over a small caption.
         int cap = UiFont.capHeight(FontSize.SMALL);
         int block = GlassIcon.SIZE + 5 + cap;
         int top = y1 + (y2 - y1 - block) / 2;
         this.icon.draw(g, (x1 + x2 - GlassIcon.SIZE) / 2, top, Glass.fade(Glass.lerp(base, accent, 0.55F * lit), opacity));
         String caption = UiFont.ellipsize(this.label, x2 - x1 - 4, FontSize.SMALL, FontWeight.REGULAR);
         UiFont.drawCentered(g, caption, (x1 + x2) / 2, top + GlassIcon.SIZE + 5, color, FontSize.SMALL, FontWeight.REGULAR, false);
         return;
      }

      int textY = UiFont.centerY(y1, y2 - y1);
      int pad = this.plain ? Theme.SPACE_XS : (this.leftAligned ? Theme.BUTTON_PAD + Math.max(0, this.radius - Theme.RADIUS) : Theme.SPACE_M);
      int right = x2 - pad;
      int count = this.badge == null ? 0 : this.badge.getAsInt();
      if (count > 0) {
         String text = count > 99 ? "99+" : Integer.toString(count);
         int bw = UiFont.width(text) + 6;
         int by = y1 + (y2 - y1 - 11) / 2;
         Glass.roundRect(g, right - bw, by, right, by + 11, 5, Glass.fade(Glass.withAlpha(this.badgeColor, 170), opacity));
         UiFont.draw(g, text, right - bw + 3, UiFont.centerY(by, 11), Glass.fade(Glass.text(), opacity), false);
         right -= bw + Theme.SPACE_S;
      }

      if (this.detail != null && this.switchState == null) {
         if (this.detailWidth < 0) {
            this.detailWidth = UiFont.width(this.detail);
         }

         int room = Math.max(0, (right - x1 - pad) - this.labelWidth() - Theme.SPACE_L);
         String shownDetail = this.detailWidth <= room ? this.detail : UiFont.ellipsize(this.detail, room);
         if (!shownDetail.isEmpty()) {
            int dw = shownDetail == this.detail ? this.detailWidth : UiFont.width(shownDetail);
            UiFont.draw(g, shownDetail, right - dw, textY, Glass.fade(Glass.textFaint(), opacity), false);
            right -= dw + Theme.SPACE_L;
         }
      }

      if (this.switchState == null) {
         int left = x1 + pad;
         boolean bare = this.iconOnly && this.icon != null;
         int iconRoom = this.icon == null ? 0 : (bare ? GlassIcon.SIZE : GlassIcon.SIZE + Theme.SPACE_S + 1);
         String shown = bare ? "" : this.fit(right - left - iconRoom);
         int width = bare ? 0 : (shown == this.label ? this.labelWidth() : UiFont.width(shown, FontSize.BODY, weight));
         int centre = count > 0 ? (left + right) / 2 : (x1 + x2) / 2;
         int start = this.leftAligned ? left : centre - (width + iconRoom) / 2;
         if (this.icon != null) {
            this.icon.draw(g, start, y1 + (y2 - y1 - GlassIcon.SIZE) / 2, color);
         }

         if (!bare) {
            UiFont.draw(g, shown, start + iconRoom, textY, color, FontSize.BODY, weight, false);
         }
      } else {
         boolean on = this.switchState.getAsBoolean();
         this.knob = Glass.approach(this.knob, on ? 1.0F : 0.0F, 12.0F, delta);
         int switchX = right - 18;
         int left = x1 + pad;
         UiFont.draw(g, this.fit(switchX - Theme.SPACE_S - left), left, textY, color, false);
         Glass.toggle(g, switchX, y1 + (y2 - y1 - 10) / 2, this.knob, accent, opacity);
      }
   }

   private int labelWidth() {
      if (this.labelWidth < 0) {
         this.labelWidth = UiFont.width(this.label, FontSize.BODY, this.primary ? FontWeight.MEDIUM : FontWeight.REGULAR);
      }

      return this.labelWidth;
   }

   /** The label ellipsized to {@code room}, cached per width (the card grows while hovered, so it changes). */
   private String fit(int room) {
      if (this.labelWidth() <= room) {
         return this.label;
      }

      if (this.fittedFor != room) {
         this.fittedFor = room;
         this.fitted = UiFont.ellipsize(this.label, room, FontSize.BODY, this.primary ? FontWeight.MEDIUM : FontWeight.REGULAR);
      }

      return this.fitted;
   }
}
