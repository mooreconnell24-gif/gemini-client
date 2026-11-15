package gemini.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

/**
 * A vanilla button drawn as a Gemini glass card. Being a real {@link Button} it keeps vanilla focus handling
 * (Tab, arrow keys, Enter/Space), narration, tooltips and click sounds; only the look is replaced: the card
 * widens slightly and gains an accent glow while hovered or focused.
 *
 * <p>With a {@code switchState} the button is drawn as a labelled on/off switch instead.
 */
public class GlassButton extends Button {
   private final BooleanSupplier switchState;
   private String label;
   private int labelWidth = -1;
   private float hover;
   private float knob = Float.NaN;
   private long lastFrame;
   private int expand = 3;

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

   @Override
   public void setMessage(Component message) {
      super.setMessage(message);
      this.label = message.getString();
      this.labelWidth = -1;
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
      this.hover = Glass.approach(this.hover, lit ? 1.0F : 0.0F, 14.0F, delta);
      float opacity = this.alpha * (this.active ? 1.0F : 0.55F);
      // Below this the card is invisible anyway, and text with next to no alpha is not reliably drawn faded.
      if (opacity <= 0.05F) {
         return;
      }

      int grow = Math.round(this.expand * this.hover);
      int x1 = this.getX() - grow;
      int x2 = this.getRight() + grow;
      int y1 = this.getY();
      int y2 = this.getBottom();
      int accent = Glass.accent();
      Glass.panel(g, x1, y1, x2, y2, Glass.RADIUS, Glass.surface(this.hover > 0.5F ? 2 : 0), accent, opacity, this.hover);
      // Accent tick on the left edge that lengthens with the hover animation.
      int tick = Math.round((y2 - y1 - 8) * (0.35F + 0.65F * this.hover) / 2.0F);
      int middle = (y1 + y2) / 2;
      g.fill(x1 + 3, middle - tick, x1 + 4, middle + tick, Glass.fade(Glass.lerp(accent, Glass.accent2(), this.hover), opacity * (0.45F + 0.55F * this.hover)));
      Font font = Minecraft.getInstance().font;
      if (this.labelWidth < 0) {
         this.labelWidth = font.width(this.label);
      }

      int color = Glass.fade(this.active ? Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, 0.35F + 0.65F * this.hover) : Glass.TEXT_FAINT, this.alpha);
      int textY = y1 + (y2 - y1 - 8) / 2;
      if (this.switchState == null) {
         g.text(font, this.label, (x1 + x2 - this.labelWidth) / 2, textY, color, false);
      } else {
         boolean on = this.switchState.getAsBoolean();
         this.knob = Glass.approach(this.knob, on ? 1.0F : 0.0F, 12.0F, delta);
         g.text(font, this.label, x1 + 9, textY, color, false);
         Glass.toggle(g, x2 - 24, y1 + (y2 - y1 - 10) / 2, this.knob, Glass.fade(on ? Glass.OK : accent, opacity));
      }
   }
}
